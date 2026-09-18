// Video conversion. Every frame is painted exactly the way the preview paints
// it, then re-encoded, so a clip comes out of the same editor as a still POP.
import { Muxer, ArrayBufferTarget } from './vendor/mp4-muxer.mjs';

// H.264 first: it is the only video codec Instagram reliably accepts.
const VIDEO_CANDIDATES = [
  { codec: 'avc1.4d0028', track: 'avc', label: 'H.264' },
  { codec: 'avc1.42001f', track: 'avc', label: 'H.264' },
  { codec: 'vp09.00.10.08', track: 'vp9', label: 'VP9' },
];

const AUDIO_CANDIDATES = [
  { codec: 'mp4a.40.2', track: 'aac', label: 'AAC' },
  { codec: 'opus', track: 'opus', label: 'Opus' },
];

/** Whether this browser can re-encode video at all. */
export function videoSupported() {
  return typeof VideoEncoder !== 'undefined' &&
    typeof VideoFrame !== 'undefined' &&
    'requestVideoFrameCallback' in HTMLVideoElement.prototype;
}

function bitrateFor(width, height) {
  const perPixel = 0.11;
  const raw = Math.round(width * height * 30 * perPixel);
  return Math.min(9_000_000, Math.max(3_000_000, raw));
}

async function pickVideoCodec(width, height, bitrate) {
  // A phone's built-in encoder is many times faster than the software one, so
  // it is asked for first; desktops without one fall through to no preference.
  for (const hardwareAcceleration of ['prefer-hardware', 'no-preference']) {
    for (const candidate of VIDEO_CANDIDATES) {
      try {
        const config = {
          codec: candidate.codec, width, height, bitrate, framerate: 30, hardwareAcceleration,
        };
        const support = await VideoEncoder.isConfigSupported(config);
        if (support && support.supported) return { ...candidate, hardwareAcceleration };
      } catch (err) { /* try the next one */ }
    }
  }
  return null;
}

async function pickAudioCodec(sampleRate, numberOfChannels) {
  if (typeof AudioEncoder === 'undefined') return null;
  for (const candidate of AUDIO_CANDIDATES) {
    try {
      const support = await AudioEncoder.isConfigSupported({
        codec: candidate.codec, sampleRate, numberOfChannels, bitrate: 128_000,
      });
      if (support && support.supported) return candidate;
    } catch (err) { /* try the next one */ }
  }
  return null;
}

/** Decodes the whole soundtrack up front; nothing is re-encoded if it fails. */
async function decodeAudio(file) {
  const Ctor = window.AudioContext || window.webkitAudioContext;
  if (!Ctor) return null;
  const context = new Ctor();
  try {
    const buffer = await context.decodeAudioData(await file.arrayBuffer());
    return buffer && buffer.length ? buffer : null;
  } catch (err) {
    return null;                 // no audio track, or one this browser cannot read
  } finally {
    if (context.close) context.close();
  }
}

async function encodeAudio(buffer, codec, muxer) {
  const channels = Math.min(2, buffer.numberOfChannels);
  const sampleRate = buffer.sampleRate;
  let failure = null;
  const encoder = new AudioEncoder({
    output: (chunk, meta) => muxer.addAudioChunk(chunk, meta),
    error: (err) => { failure = err; },
  });
  encoder.configure({ codec: codec.codec, sampleRate, numberOfChannels: channels, bitrate: 128_000 });

  const span = 16_384;
  const scratch = new Float32Array(span);
  for (let offset = 0; offset < buffer.length && !failure; offset += span) {
    const count = Math.min(span, buffer.length - offset);
    const planar = new Float32Array(count * channels);
    for (let channel = 0; channel < channels; channel++) {
      buffer.copyFromChannel(scratch.subarray(0, count), channel, offset);
      planar.set(scratch.subarray(0, count), channel * count);
    }
    const data = new AudioData({
      format: 'f32-planar',
      sampleRate,
      numberOfFrames: count,
      numberOfChannels: channels,
      timestamp: Math.round((offset / sampleRate) * 1e6),
      data: planar,
    });
    encoder.encode(data);
    data.close();
    if (encoder.encodeQueueSize > 24) await new Promise((r) => setTimeout(r, 0));
  }
  await encoder.flush();
  encoder.close();
  if (failure) throw failure;
  return { channels, sampleRate };
}

/**
 * Re-encodes `file` into the given frame.
 *
 * Two ways in. The fast one demuxes and decodes the file directly, so it runs
 * as fast as the encoder can take frames rather than at playback speed. If the
 * browser cannot read that particular file, the slow one falls back to reading
 * frames off ordinary playback, which works with anything it can play at all.
 *
 * `paint(ctx, width, height, source)` draws one composed frame — the caller
 * owns the backdrop and the placement, exactly as on screen.
 */
export async function convertVideo(options) {
  try {
    return await convertDecoded(options);
  } catch (err) {
    if (err && err.encoded) throw err;   // frames were already written: no retry
    if (options.onProgress) options.onProgress(0);
    return await convertByPlayback(options);
  }
}

let readerPromise = null;
function reader() {
  if (!readerPromise) readerPromise = import('./vendor/mediabunny.mjs?v=__BUILD__');
  return readerPromise;
}

/** Starts loading the reader as soon as a clip is opened, not when it is due. */
export function warmUp() {
  if (videoSupported()) reader().catch(() => {});
}

async function convertDecoded({ file, width, height, paint, onProgress }) {
  const { Input, ALL_FORMATS, BlobSource, VideoSampleSink } = await reader();

  const input = new Input({ source: new BlobSource(file), formats: ALL_FORMATS });
  const track = await input.getPrimaryVideoTrack();
  if (!track) throw new Error('映像トラックがありません');
  if (!(await track.canDecode())) throw new Error('この形式は直接デコードできません');

  const duration = await input.computeDuration();
  if (!duration) throw new Error('長さが読めません');

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');

  const stage = document.createElement('canvas');
  const stageCtx = stage.getContext('2d');

  const kit = await openEncoder({ file, width, height });
  let frames = 0;
  let lastTimestamp = -1;
  let lastKeyframe = -Infinity;

  try {
    const sink = new VideoSampleSink(track);
    for await (const sample of sink.samples()) {
      try {
        if (kit.encoderError) throw kit.encoderError;
        const timestamp = Math.round(sample.timestamp * 1e6);
        if (timestamp <= lastTimestamp) continue;

        // Rotation and pixel aspect live on the sample, and drawing it into a
        // canvas of its display size is what applies them.
        const w = Math.max(1, Math.round(sample.displayWidth || sample.squarePixelWidth || sample.codedWidth));
        const h = Math.max(1, Math.round(sample.displayHeight || sample.squarePixelHeight || sample.codedHeight));
        if (stage.width !== w || stage.height !== h) {
          stage.width = w;
          stage.height = h;
        }
        sample.draw(stageCtx, 0, 0, w, h);
        paint(ctx, width, height, stage);

        const keyFrame = timestamp - lastKeyframe >= 2e6;
        if (keyFrame) lastKeyframe = timestamp;
        const frame = new VideoFrame(canvas, { timestamp });
        kit.encoder.encode(frame, { keyFrame });
        frame.close();
        lastTimestamp = timestamp;
        frames++;
        if (onProgress) onProgress(Math.min(0.99, sample.timestamp / duration));
        while (kit.encoder.encodeQueueSize > 8) {
          await new Promise((r) => setTimeout(r, 0));
        }
      } finally {
        sample.close();
      }
    }
  } catch (err) {
    kit.abort();
    if (frames > 0) err.encoded = true;
    throw err;
  }

  if (!frames) {
    kit.abort();
    throw new Error('フレームを読み取れませんでした');
  }
  const result = await kit.finish();
  if (onProgress) onProgress(1);
  return { ...result, frames, path: 'decoded' };
}

/** Codec choice, the muxer, the soundtrack and the encoder, in one piece. */
async function openEncoder({ file, width, height }) {
  if (!videoSupported()) throw new Error('この端末は動画の書き出しに対応していません');

  const bitrate = bitrateFor(width, height);
  const videoCodec = await pickVideoCodec(width, height, bitrate);
  if (!videoCodec) throw new Error('この端末では動画を書き出せません');

  const sound = await decodeAudio(file);
  const audioCodec = sound
    ? await pickAudioCodec(sound.sampleRate, Math.min(2, sound.numberOfChannels))
    : null;

  const muxer = new Muxer({
    target: new ArrayBufferTarget(),
    video: { codec: videoCodec.track, width, height, frameRate: 30 },
    audio: audioCodec
      ? {
        codec: audioCodec.track,
        numberOfChannels: Math.min(2, sound.numberOfChannels),
        sampleRate: sound.sampleRate,
      }
      : undefined,
    fastStart: 'in-memory',
    firstTimestampBehavior: 'offset',
  });

  // Sound first: it is quick, and a failure there should not cost a whole pass
  // over the frames.
  let audioWritten = false;
  if (sound && audioCodec) {
    try {
      await encodeAudio(sound, audioCodec, muxer);
      audioWritten = true;
    } catch (err) {
      audioWritten = false;
    }
  }

  const kit = { encoderError: null, videoCodec };
  kit.encoder = new VideoEncoder({
    output: (chunk, meta) => muxer.addVideoChunk(chunk, meta),
    error: (err) => { kit.encoderError = err; },
  });
  kit.encoder.configure({
    codec: videoCodec.codec,
    width,
    height,
    bitrate,
    framerate: 30,
    latencyMode: 'quality',
    hardwareAcceleration: videoCodec.hardwareAcceleration || 'no-preference',
    avc: videoCodec.track === 'avc' ? { format: 'avc' } : undefined,
  });

  kit.abort = () => {
    try { kit.encoder.close(); } catch (err) { /* already gone */ }
  };

  kit.finish = async () => {
    await kit.encoder.flush();
    kit.encoder.close();
    if (kit.encoderError) throw kit.encoderError;
    muxer.finalize();
    return {
      blob: new Blob([muxer.target.buffer], { type: 'video/mp4' }),
      codec: videoCodec.label,
      instagramReady: videoCodec.track === 'avc',
      hardware: videoCodec.hardwareAcceleration === 'prefer-hardware',
      audio: audioWritten,
    };
  };

  return kit;
}

/**
 * The fallback: read frames off ordinary playback. Slower, since it can only
 * go as fast as the browser plays, but it works with anything that plays.
 */
async function convertByPlayback({ file, video, width, height, paint, onProgress, speed }) {
  const duration = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : 0;
  if (!duration) throw new Error('動画の長さが読めません');

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');

  const kit = await openEncoder({ file, width, height });

  const wasLooping = video.loop;
  const wasMuted = video.muted;
  const wasRate = video.playbackRate;
  video.loop = false;
  video.muted = true;
  // Playback sets the pace here, and a browser quietly stops decoding frames
  // when pushed much past real time, so this stays close to it.
  video.playbackRate = Math.min(2, Math.max(1, speed || 1));

  let lastTimestamp = -1;
  let lastKeyframe = -Infinity;
  let frames = 0;
  let finished = false;

  try {
    await new Promise((resolve, reject) => {
      let watchdog = null;
      let pump = null;

      const stop = (err) => {
        if (finished) return;
        finished = true;
        clearInterval(pump);
        clearTimeout(watchdog);
        video.onended = null;
        video.onerror = null;
        if (err) reject(err); else resolve();
      };

      const arm = () => {
        clearTimeout(watchdog);
        watchdog = setTimeout(() => stop(new Error('変換が進まなくなりました')), 30_000);
      };

      const onFrame = (_now, meta) => {
        if (finished) return;
        if (kit.encoderError) { stop(kit.encoderError); return; }
        const timestamp = Math.round((meta.mediaTime || 0) * 1e6);
        if (timestamp > lastTimestamp) {
          paint(ctx, width, height, video);
          const keyFrame = timestamp - lastKeyframe >= 2e6;
          if (keyFrame) lastKeyframe = timestamp;
          const frame = new VideoFrame(canvas, { timestamp });
          kit.encoder.encode(frame, { keyFrame });
          frame.close();
          lastTimestamp = timestamp;
          frames++;
          if (onProgress) onProgress(Math.min(0.99, (meta.mediaTime || 0) / duration));
          arm();
        }
        video.requestVideoFrameCallback(onFrame);
      };

      // The encoder can be slower than playback on a phone, so playback waits
      // for it rather than dropping the frames it cannot keep up with.
      pump = setInterval(() => {
        if (finished) return;
        if (kit.encoder.encodeQueueSize > 12 && !video.paused) video.pause();
        else if (kit.encoder.encodeQueueSize < 4 && video.paused) video.play().catch(() => {});
      }, 100);

      video.onended = () => stop(null);
      video.onerror = () => stop(new Error('動画を再生できませんでした'));
      video.requestVideoFrameCallback(onFrame);
      arm();
      try {
        video.currentTime = 0;
      } catch (err) { /* some sources refuse a seek; playback still starts */ }
      video.play().then(arm).catch((err) => stop(err));
    });
  } catch (err) {
    kit.abort();
    throw err;
  } finally {
    video.loop = wasLooping;
    video.muted = wasMuted;
    video.playbackRate = wasRate;
  }

  if (!frames) {
    kit.abort();
    throw new Error('フレームを読み取れませんでした');
  }

  const result = await kit.finish();
  if (onProgress) onProgress(1);
  return { ...result, frames, path: 'playback' };
}
