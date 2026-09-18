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
  for (const candidate of VIDEO_CANDIDATES) {
    try {
      const config = { codec: candidate.codec, width, height, bitrate, framerate: 30 };
      const support = await VideoEncoder.isConfigSupported(config);
      if (support && support.supported) return candidate;
    } catch (err) { /* try the next one */ }
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
 * `paint(ctx, width, height)` draws one composed frame — the caller owns the
 * backdrop and the placement, exactly as on screen.
 */
export async function convertVideo({ file, video, width, height, paint, onProgress }) {
  if (!videoSupported()) throw new Error('この端末は動画の書き出しに対応していません');

  const duration = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : 0;
  if (!duration) throw new Error('動画の長さが読めません');

  const bitrate = bitrateFor(width, height);
  const videoCodec = await pickVideoCodec(width, height, bitrate);
  if (!videoCodec) throw new Error('この端末では動画を書き出せません');

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');

  const sound = await decodeAudio(file);
  const audioCodec = sound ? await pickAudioCodec(sound.sampleRate, Math.min(2, sound.numberOfChannels)) : null;

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

  // Sound first: it is quick, and a failure there should not waste a whole
  // pass over the frames.
  let audioWritten = false;
  if (sound && audioCodec) {
    try {
      await encodeAudio(sound, audioCodec, muxer);
      audioWritten = true;
    } catch (err) {
      audioWritten = false;
    }
  }

  let encoderError = null;
  const encoder = new VideoEncoder({
    output: (chunk, meta) => muxer.addVideoChunk(chunk, meta),
    error: (err) => { encoderError = err; },
  });
  encoder.configure({
    codec: videoCodec.codec,
    width,
    height,
    bitrate,
    framerate: 30,
    latencyMode: 'quality',
    avc: videoCodec.track === 'avc' ? { format: 'avc' } : undefined,
  });

  const wasLooping = video.loop;
  const wasMuted = video.muted;
  video.loop = false;
  video.muted = true;

  let lastTimestamp = -1;
  let lastKeyframe = -Infinity;
  let frames = 0;
  let finished = false;

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
      if (encoderError) { stop(encoderError); return; }
      const timestamp = Math.round((meta.mediaTime || 0) * 1e6);
      if (timestamp > lastTimestamp) {
        paint(ctx, width, height);
        const keyFrame = timestamp - lastKeyframe >= 2e6;
        if (keyFrame) lastKeyframe = timestamp;
        const frame = new VideoFrame(canvas, { timestamp });
        encoder.encode(frame, { keyFrame });
        frame.close();
        lastTimestamp = timestamp;
        frames++;
        if (onProgress) onProgress(Math.min(0.99, (meta.mediaTime || 0) / duration));
        arm();
      }
      video.requestVideoFrameCallback(onFrame);
    };

    // The encoder is slower than playback on a phone, so playback waits for it
    // rather than dropping the frames it cannot keep up with.
    pump = setInterval(() => {
      if (finished) return;
      if (encoder.encodeQueueSize > 12 && !video.paused) video.pause();
      else if (encoder.encodeQueueSize < 4 && video.paused) video.play().catch(() => {});
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

  await encoder.flush();
  encoder.close();
  if (encoderError) throw encoderError;
  if (!frames) throw new Error('フレームを読み取れませんでした');

  muxer.finalize();
  video.loop = wasLooping;
  video.muted = wasMuted;
  if (onProgress) onProgress(1);

  return {
    blob: new Blob([muxer.target.buffer], { type: 'video/mp4' }),
    codec: videoCodec.label,
    instagramReady: videoCodec.track === 'avc',
    audio: audioWritten,
    frames,
  };
}
