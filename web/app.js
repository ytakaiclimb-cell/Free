// POP → INSTAGRAM, the browser edition of the Android app in this repo.
// Same geometry, same backdrops, same output: a 1080 px wide post.

import { convertVideo, videoSupported, warmUp } from './video.js?v=__BUILD__';
import { zipStore } from './zip.js?v=__BUILD__';

const FORMATS = {
  square:   { key: 'square',   ratio: '1:1',  label: '正方形',     width: 1080, height: 1080 },
  portrait: { key: 'portrait', ratio: '4:5',  label: '縦長',       width: 1080, height: 1350 },
  story:    { key: 'story',    ratio: '9:16', label: 'ストーリー', width: 1080, height: 1920 },
};

const MIN_ZOOM = 0.3;
const MAX_ZOOM = 5;
const MAX_EDGE = 2600;    // what a POP is decoded to; plenty for a 1080 px post
const PREVIEW_EDGE = 900; // what the on-screen copy is drawn from
const CREAM = '#f2f0e6';
const INK = '#141416';

// What belongs to one opened file rather than to the editor as a whole.
const ITEM_FIELDS = ['kind', 'source', 'preview', 'srcW', 'srcH', 'duration', 'made',
  'paper', 'name', 'file', 'pdf', 'pageCount', 'pageIndex', 'layout', 'thumb'];

const state = {
  items: [],        // everything opened, in the order it was opened
  active: 0,
  kind: 'image',    // 'image' or 'video'
  source: null,     // full resolution canvas, or the <video> element
  preview: null,    // smaller copy, redrawn on every gesture
  srcW: 0,
  srcH: 0,
  duration: 0,
  made: null,       // the last exported video, kept so sharing stays one tap
  blurTile: null,   // tiny cover-cropped copy, stretched into the blur backdrop
  paper: '#ffffff',
  name: '',
  pdf: null,
  pageCount: 1,
  pageIndex: 0,
  format: FORMATS.portrait,
  mode: 'contain',
  backdrop: 'paper',
  margin: 0.04,
  layout: { zoom: 1, x: 0, y: 0 },
  busy: false,
  progress: -1,
  batch: null,      // everything the last "export all" produced
  file: null,       // what was opened, kept for the video soundtrack
};

// Stamped by the publishing workflow; "dev" when served straight from disk.
const rawBuild = document.body.getAttribute('data-build') || '';
const BUILD = /^[0-9]/.test(rawBuild) ? rawBuild : 'dev';

const els = {};
for (const id of ['subtitle', 'openPhoto', 'openPdf', 'openVideo',
  'openPhotoBig', 'openPdfBig', 'openVideoBig',
  'fileImage', 'filePdf', 'fileVideo', 'stage', 'empty', 'preview', 'media', 'formats',
  'modes', 'backdrops', 'margin', 'marginOut', 'pages', 'pageLabel', 'prevPage', 'nextPage',
  'reset', 'saveAll', 'save', 'share', 'toast', 'progress', 'progressFill', 'phoneHint',
  'strip', 'stripHead', 'stripCount', 'clear', 'batchRow', 'batch']) {
  els[id] = document.getElementById(id);
}

// ---- geometry: shared by the preview and the export ------------------------

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function baseScale(sw, sh, iw, ih, mode) {
  const w = iw / Math.max(1, sw);
  const h = ih / Math.max(1, sh);
  return mode === 'contain' ? Math.min(w, h) : Math.max(w, h);
}

function normalize(layout, sw, sh, fw, fh, margin, mode) {
  const zoom = clamp(layout.zoom, MIN_ZOOM, MAX_ZOOM);
  const pad = margin * Math.min(fw, fh);
  const iw = Math.max(1, fw - pad * 2);
  const ih = Math.max(1, fh - pad * 2);
  const b = baseScale(sw, sh, iw, ih, mode);
  const dw = sw * b * zoom;
  const dh = sh * b * zoom;
  // Either the POP is larger than its frame and may be panned until an edge
  // lines up, or it is smaller and may be slid until it touches one.
  const lx = Math.abs(dw - iw) / 2 / fw;
  const ly = Math.abs(dh - ih) / 2 / fh;
  return { zoom, x: clamp(layout.x, -lx, lx), y: clamp(layout.y, -ly, ly) };
}

function place(layout, sw, sh, fw, fh, margin, mode) {
  const safe = normalize(layout, sw, sh, fw, fh, margin, mode);
  const pad = margin * Math.min(fw, fh);
  const iw = Math.max(1, fw - pad * 2);
  const ih = Math.max(1, fh - pad * 2);
  const b = baseScale(sw, sh, iw, ih, mode);
  const w = sw * b * safe.zoom;
  const h = sh * b * safe.zoom;
  return {
    left: pad + (iw - w) / 2 + safe.x * fw,
    top: pad + (ih - h) / 2 + safe.y * fh,
    width: w,
    height: h,
  };
}

// ---- bitmap helpers --------------------------------------------------------

function canvasOf(w, h) {
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(w));
  c.height = Math.max(1, Math.round(h));
  return c;
}

/** Halving before the final draw is what keeps small print readable. */
function reduce(src, targetWidth) {
  let cur = src;
  while (cur.width / 2 >= targetWidth && cur.width / 2 >= 1 && cur.height / 2 >= 1) {
    const next = canvasOf(cur.width / 2, cur.height / 2);
    const ctx = next.getContext('2d');
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(cur, 0, 0, next.width, next.height);
    cur = next;
  }
  return cur;
}

/** The average colour of the POP's outermost ring — its paper, usually. */
function paperColor(src) {
  const n = 24;
  const c = canvasOf(n, n);
  const ctx = c.getContext('2d', { willReadFrequently: true });
  ctx.drawImage(src, 0, 0, n, n);
  let r = 0, g = 0, b = 0, count = 0;
  const d = ctx.getImageData(0, 0, n, n).data;
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (x !== 0 && y !== 0 && x !== n - 1 && y !== n - 1) continue;
      const i = (y * n + x) * 4;
      r += d[i]; g += d[i + 1]; b += d[i + 2]; count++;
    }
  }
  if (!count) return '#ffffff';
  return `rgb(${Math.round(r / count)}, ${Math.round(g / count)}, ${Math.round(b / count)})`;
}

/** A 48 px cover-cropped copy; stretched back up it reads as a blur. */
function blurTile(src, aspect, srcW, srcH, into) {
  const w = 48;
  const h = Math.max(1, Math.round(w / aspect));
  const c = into && into.width === w && into.height === h ? into : canvasOf(w, h);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);
  // A canvas is reduced in steps first; a video frame is already small enough
  // to take in one draw, and has to be, since this runs every frame.
  const small = src instanceof HTMLCanvasElement
    ? reduce(src, w * 6)
    : { width: srcW, height: srcH, drawable: src };
  const drawable = small.drawable || small;
  const sa = small.width / small.height;
  const oa = w / h;
  let sx = 0, sy = 0, sw = small.width, sh = small.height;
  if (sa > oa) {
    sw = Math.max(1, Math.round(small.height * oa));
    sx = Math.round((small.width - sw) / 2);
  } else {
    sh = Math.max(1, Math.round(small.width / oa));
    sy = Math.round((small.height - sh) / 2);
  }
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(drawable, sx, sy, sw, sh, 0, 0, w, h);
  return c;
}

function rebuildTile(aspect, reuse) {
  if (!state.srcW) return null;
  return blurTile(state.source, aspect, state.srcW, state.srcH, reuse);
}

function backdropColor() {
  switch (state.backdrop) {
    case 'paper': return state.paper;
    case 'white': return '#ffffff';
    case 'cream': return CREAM;
    case 'ink': return INK;
    default: return '#000000';
  }
}

// ---- drawing ---------------------------------------------------------------

function compose(ctx, W, H, src, tile, p) {
  ctx.save();
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  if (state.backdrop === 'blur' && tile) {
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, W, H);
    ctx.drawImage(tile, 0, 0, W, H);
    ctx.fillStyle = 'rgba(0, 0, 0, 0.2)';
    ctx.fillRect(0, 0, W, H);
  } else {
    ctx.fillStyle = backdropColor();
    ctx.fillRect(0, 0, W, H);
  }
  const pad = state.margin * Math.min(W, H);
  ctx.beginPath();
  ctx.rect(pad, pad, W - pad * 2, H - pad * 2);
  ctx.clip();
  ctx.drawImage(src, p.left, p.top, p.width, p.height);
  ctx.restore();
}

function draw() {
  if (!state.srcW) return;
  const c = els.preview;
  const ctx = c.getContext('2d');
  // The blur follows a moving picture, so it is rebuilt from the live frame.
  if (state.kind === 'video' && state.backdrop === 'blur') {
    state.blurTile = rebuildTile(state.format.width / state.format.height, state.blurTile);
  }
  const p = place(state.layout, state.srcW, state.srcH,
    c.width, c.height, state.margin, state.mode);
  compose(ctx, c.width, c.height, state.preview, state.blurTile, p);
}

function fitPreview() {
  if (!state.srcW) return;
  const f = state.format;
  const box = els.stage.getBoundingClientRect();
  const wide = window.innerWidth >= 900;
  const availW = Math.max(160, box.width);
  const availH = Math.max(200, Math.min(window.innerHeight * (wide ? 0.78 : 0.62), wide ? 780 : 620));
  let w = availW;
  let h = (w * f.height) / f.width;
  if (h > availH) {
    h = availH;
    w = (h * f.width) / f.height;
  }
  const c = els.preview;
  c.style.width = `${Math.round(w)}px`;
  c.style.height = `${Math.round(h)}px`;
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  c.width = Math.round(w * dpr);
  c.height = Math.round(h * dpr);
}

function renderTo(format) {
  const c = canvasOf(format.width, format.height);
  const ctx = c.getContext('2d');
  paintFrame(ctx, format.width, format.height, true);
  return c;
}

/**
 * One composed frame at any size — the preview, a JPEG, or a video frame.
 * `override` is the decoded frame to draw when the video exporter supplies one.
 */
function paintFrame(ctx, W, H, fullResolution, override) {
  const drawable = override || state.source;
  const tile = state.backdrop === 'blur'
    ? blurTile(drawable, W / H, state.srcW, state.srcH, null)
    : null;
  const p = place(state.layout, state.srcW, state.srcH, W, H, state.margin, state.mode);
  const src = fullResolution && state.kind === 'image'
    ? reduce(state.source, Math.round(p.width))
    : drawable;
  compose(ctx, W, H, src, tile, p);
}

// ---- loading ---------------------------------------------------------------

function decodeImage(file) {
  // An <img> applies the photo's own rotation for us, on every browser that
  // matters, which createImageBitmap does not do everywhere.
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('画像として開けません')); };
    img.src = url;
  });
}

function cap(img) {
  const w = img.naturalWidth || img.width;
  const h = img.naturalHeight || img.height;
  if (!w || !h) throw new Error('サイズが読めません');
  const longest = Math.max(w, h);
  const scale = longest > MAX_EDGE ? MAX_EDGE / longest : 1;
  const c = canvasOf(w * scale, h * scale);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#ffffff';       // a transparent PNG would turn black in a JPEG
  ctx.fillRect(0, 0, c.width, c.height);
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(img, 0, 0, c.width, c.height);
  return c;
}

let pdfLibPromise = null;
function pdfLib() {
  if (!pdfLibPromise) {
    pdfLibPromise = import('./vendor/pdf.mjs').then((lib) => {
      lib.GlobalWorkerOptions.workerSrc = new URL('./vendor/pdf.worker.mjs', import.meta.url).href;
      return lib;
    });
  }
  return pdfLibPromise;
}

/** Builds one entry from a file without disturbing what is on screen. */
async function loadItem(file) {
  const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
  const isVideo = (file.type && file.type.indexOf('video/') === 0) ||
    /\.(mp4|mov|m4v|webm|avi|3gp|mkv)$/i.test(file.name);

  if (isVideo) {
    if (!videoSupported()) throw new Error('この端末のブラウザは動画の書き出しに未対応です');
    const probe = await probeVideo(file);
    return {
      kind: 'video', name: file.name, file,
      source: null, preview: null, thumb: probe.thumb,
      srcW: probe.width, srcH: probe.height, duration: probe.duration,
      paper: paperColor(probe.thumb),
      pdf: null, pageCount: 1, pageIndex: 0,
      layout: { zoom: 1, x: 0, y: 0 }, made: null,
    };
  }

  if (isPdf) {
    const lib = await pdfLib();
    const data = new Uint8Array(await file.arrayBuffer());
    const doc = await lib.getDocument({
      data,
      cMapUrl: new URL('./vendor/cmaps/', import.meta.url).href,
      cMapPacked: true,
    }).promise;
    const canvas = await renderPdfPage(doc, 0);
    return stillItem(canvas, file, { pdf: doc, pageCount: doc.numPages, pageIndex: 0 });
  }

  return stillItem(cap(await decodeImage(file)), file, {});
}

function stillItem(canvas, file, extra) {
  return {
    kind: 'image', name: file.name, file,
    source: canvas, preview: reduce(canvas, PREVIEW_EDGE), thumb: canvas,
    srcW: canvas.width, srcH: canvas.height, duration: 0,
    paper: paperColor(canvas),
    pdf: null, pageCount: 1, pageIndex: 0,
    layout: { zoom: 1, x: 0, y: 0 }, made: null,
    ...extra,
  };
}

async function renderPdfPage(doc, index) {
  const page = await doc.getPage(index + 1);
  const unit = page.getViewport({ scale: 1 });
  const scale = MAX_EDGE / Math.max(unit.width, unit.height);
  const viewport = page.getViewport({ scale });
  const canvas = canvasOf(viewport.width, viewport.height);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';       // a PDF paints nothing where the paper is blank
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  await page.render({ canvasContext: ctx, viewport }).promise;
  return canvas;
}

/** Reads a clip's size and first frame without taking over the player. */
function probeVideo(file) {
  return new Promise((resolve, reject) => {
    const probe = document.createElement('video');
    probe.muted = true;
    probe.playsInline = true;
    probe.preload = 'auto';
    const url = URL.createObjectURL(file);
    const fail = (message) => {
      URL.revokeObjectURL(url);
      reject(new Error(message));
    };
    probe.onerror = () => fail('この形式の動画は開けません');
    probe.onloadeddata = () => {
      const width = probe.videoWidth;
      const height = probe.videoHeight;
      if (!width) { fail('映像を読めません'); return; }
      const thumb = canvasOf(Math.min(width, 640), Math.min(width, 640) * height / width);
      thumb.getContext('2d').drawImage(probe, 0, 0, thumb.width, thumb.height);
      const duration = Number.isFinite(probe.duration) ? probe.duration : 0;
      URL.revokeObjectURL(url);
      probe.removeAttribute('src');
      resolve({ width, height, duration, thumb });
    };
    probe.src = url;
    probe.load();
  });
}

/** Copies the working fields back into the entry they came from. */
function stash() {
  const item = state.items[state.active];
  if (!item) return;
  for (const key of ITEM_FIELDS) item[key] = state[key];
}

async function activate(index, quiet) {
  if (index < 0 || index >= state.items.length) return;
  if (state.items[state.active] && index !== state.active) stash();
  state.active = index;
  const item = state.items[index];
  for (const key of ITEM_FIELDS) state[key] = item[key];

  if (state.kind === 'video') {
    await pointPlayerAt(item);
    state.source = els.media;
    state.preview = els.media;
    if (!quiet) {
      els.media.play().catch(() => {});
      startVideoLoop();
    }
    warmUp();
  } else {
    cancelAnimationFrame(videoLoop);
    els.media.pause();
  }

  state.blurTile = rebuildTile(state.format.width / state.format.height, null);
  if (quiet) return;
  refresh();
  els.preview.hidden = false;
  els.empty.hidden = true;
  fitPreview();
  draw();
  renderStrip();
}

let playerFile = null;
function pointPlayerAt(item) {
  const media = els.media;
  if (playerFile === item.file && media.src) return Promise.resolve();
  return new Promise((resolve, reject) => {
    if (media.src) URL.revokeObjectURL(media.src);
    media.loop = true;
    media.muted = true;
    media.playsInline = true;
    media.onloadeddata = () => { media.onloadeddata = null; media.onerror = null; resolve(); };
    media.onerror = () => { media.onloadeddata = null; media.onerror = null; reject(new Error('動画を開けません')); };
    playerFile = item.file;
    media.src = URL.createObjectURL(item.file);
    media.load();
  });
}

let videoLoop = 0;
function startVideoLoop() {
  cancelAnimationFrame(videoLoop);
  const step = () => {
    if (state.kind !== 'video') return;
    draw();
    videoLoop = requestAnimationFrame(step);
  };
  videoLoop = requestAnimationFrame(step);
}

/** The row of what is open, shown as soon as there is more than one. */
function renderStrip() {
  const strip = els.strip;
  strip.hidden = state.items.length < 2;
  if (strip.hidden) { strip.textContent = ''; return; }
  strip.textContent = '';
  state.items.forEach((item, index) => {
    const cell = document.createElement('button');
    cell.className = 'cell';
    cell.setAttribute('aria-pressed', String(index === state.active));
    const thumb = canvasOf(72, 72);
    const ctx = thumb.getContext('2d');
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, 72, 72);
    const art = item.thumb || item.source;
    if (art) {
      const scale = Math.min(72 / art.width, 72 / art.height);
      const w = art.width * scale;
      const h = art.height * scale;
      ctx.drawImage(art, (72 - w) / 2, (72 - h) / 2, w, h);
    }
    thumb.className = 'shot';
    cell.appendChild(thumb);
    if (item.kind === 'video') {
      const tag = document.createElement('i');
      tag.className = 'tag';
      tag.textContent = '動画';
      cell.appendChild(tag);
    }
    cell.addEventListener('click', () => { if (!state.busy) activate(index); });
    strip.appendChild(cell);
  });
}

async function openFiles(list) {
  const files = [...(list || [])];
  if (!files.length || state.busy) return;
  setBusy(true);
  notice(null);
  const failures = [];
  const fresh = [];
  for (const file of files) {
    try {
      fresh.push(await loadItem(file));
    } catch (err) {
      failures.push(`${file.name}: ${(err && err.message) || err}`);
    }
  }
  if (fresh.length) {
    if (state.items.length && state.srcW) stash();
    const first = state.items.length;
    state.items = state.items.concat(fresh);
    setBusy(false);
    await activate(first);
    if (fresh.length > 1) toast(`${fresh.length} 件を読み込みました`);
    const long = fresh.find((item) => item.duration > 180);
    if (long) toast('長い動画です。書き出しに時間がかかります');
  } else {
    setBusy(false);
  }
  if (failures.length) notice(`読み込めませんでした（${failures[0]}）`);
  refresh();
}

function clearAll() {
  cancelAnimationFrame(videoLoop);
  if (els.media.src) {
    els.media.pause();
    URL.revokeObjectURL(els.media.src);
    els.media.removeAttribute('src');
    playerFile = null;
  }
  state.items = [];
  state.active = 0;
  state.source = null;
  state.preview = null;
  state.srcW = 0;
  state.srcH = 0;
  state.kind = 'image';
  state.name = '';
  state.made = null;
  state.pdf = null;
  state.pageCount = 1;
  state.pageIndex = 0;
  els.preview.hidden = true;
  els.empty.hidden = false;
  renderStrip();
  refresh();
}

/** Page turning inside one PDF entry. */
async function turnPage(delta) {
  if (state.busy || !state.pdf) return;
  const next = state.pageIndex + delta;
  if (next < 0 || next >= state.pageCount) return;
  setBusy(true);
  try {
    const canvas = await renderPdfPage(state.pdf, next);
    state.source = canvas;
    state.preview = reduce(canvas, PREVIEW_EDGE);
    state.thumb = canvas;
    state.srcW = canvas.width;
    state.srcH = canvas.height;
    state.paper = paperColor(canvas);
    state.pageIndex = next;
    state.layout = { zoom: 1, x: 0, y: 0 };
    state.made = null;
    state.blurTile = rebuildTile(state.format.width / state.format.height, null);
    stash();
    fitPreview();
    draw();
    renderStrip();
  } catch (err) {
    notice(`ページを開けませんでした（${(err && err.message) || err}）`);
  } finally {
    setBusy(false);
  }
}

// ---- output ----------------------------------------------------------------

function stamp(date = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}${p(date.getMonth() + 1)}${p(date.getDate())}` +
    `_${p(date.getHours())}${p(date.getMinutes())}${p(date.getSeconds())}`;
}

/** Keeps the original name, so a batch stays recognisable after unzipping. */
function baseName(name) {
  const bare = String(name || '').replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|]+/g, '_').trim();
  return bare.slice(0, 48) || `POP_${stamp()}`;
}

function outputName(format, ext = 'jpg') {
  const page = state.pdf && state.pageCount > 1 ? `_p${state.pageIndex + 1}` : '';
  return `${baseName(state.name)}${page}_${format.width}x${format.height}.${ext}`;
}

/** Two files of the same name in one bundle would overwrite each other. */
function uniqueName(name, taken) {
  if (!taken.has(name)) { taken.add(name); return name; }
  const dot = name.lastIndexOf('.');
  const stem = dot > 0 ? name.slice(0, dot) : name;
  const ext = dot > 0 ? name.slice(dot) : '';
  let n = 2;
  while (taken.has(`${stem}_${n}${ext}`)) n++;
  const fresh = `${stem}_${n}${ext}`;
  taken.add(fresh);
  return fresh;
}

function dataUrlToBlob(url) {
  const comma = url.indexOf(',');
  const binary = atob(url.slice(comma + 1));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return new Blob([bytes], { type: 'image/jpeg' });
}

/** Built synchronously: Safari only shares from inside the click itself. */
function filesFor(formats) {
  return formats.map((f) => {
    const url = renderTo(f).toDataURL('image/jpeg', 0.96);
    return new File([dataUrlToBlob(url)], outputName(f), { type: 'image/jpeg' });
  });
}

function download(file) {
  const url = URL.createObjectURL(file);
  const a = document.createElement('a');
  a.href = url;
  a.download = file.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 20000);
}

const canShareFiles = (() => {
  try {
    const probe = new File([new Uint8Array([0])], 'probe.jpg', { type: 'image/jpeg' });
    return !!(navigator.canShare && navigator.canShare({ files: [probe] }));
  } catch (e) {
    return false;
  }
})();

function setProgress(value) {
  state.progress = value;
  const running = value >= 0;
  els.progress.hidden = !running;
  els.progressFill.style.width = `${Math.round(Math.max(0, value) * 100)}%`;
  if (running) {
    els.subtitle.textContent = `書き出し中… ${Math.round(value * 100)}%`;
    els.subtitle.classList.remove('warn');
  }
}

async function exportVideo() {
  if (state.kind !== 'video' || state.busy || !state.file) return;
  const format = state.format;
  cancelAnimationFrame(videoLoop);
  // The preview keeps playing otherwise, and competes with the encoder.
  els.media.pause();
  setBusy(true);
  setProgress(0);
  notice(null);
  // Encoding follows playback, and playback stops when a phone sleeps.
  let wakeLock = null;
  try {
    if (navigator.wakeLock) wakeLock = await navigator.wakeLock.request('screen');
  } catch (err) { /* not available; the warning in the hint covers it */ }
  try {
    const result = await convertVideo({
      file: state.file,
      video: els.media,
      width: format.width,
      height: format.height,
      paint: (ctx, W, H, source) => paintFrame(ctx, W, H, false, source),
      onProgress: setProgress,
    });
    state.made = new File([result.blob], outputName(format, 'mp4'), { type: 'video/mp4' });
    if (!result.instagramReady) {
      notice(`この端末では ${result.codec} でしか書き出せませんでした。` +
        'Instagram が受け付けない場合があります');
    }
    if (canShareFiles) {
      toast('書き出しました。「Instagram / 写真に保存」から投稿・保存できます');
    } else {
      download(state.made);
      toast(result.audio ? '書き出しました（音つき）' : '書き出しました（音なし）');
    }
  } catch (err) {
    notice(`書き出せませんでした（${(err && err.message) || err}）`);
  } finally {
    if (wakeLock) wakeLock.release().catch(() => {});
    setProgress(-1);
    setBusy(false);
    els.media.loop = true;
    els.media.play().catch(() => {});
    startVideoLoop();
  }
}

async function blobBytes(blob) {
  return new Uint8Array(await blob.arrayBuffer());
}

/** Runs every open file through the current settings, one after another. */
async function exportAll() {
  if (state.busy || state.items.length < 2) return;
  const format = state.format;
  const made = [];
  const taken = new Set();
  const failures = [];
  const total = state.items.length;
  const startedAt = state.active;
  cancelAnimationFrame(videoLoop);
  els.media.pause();
  setBusy(true);
  notice(null);
  state.batch = null;

  let wakeLock = null;
  try {
    if (navigator.wakeLock) wakeLock = await navigator.wakeLock.request('screen');
  } catch (err) { /* only matters for long video runs */ }

  try {
    for (let index = 0; index < total; index++) {
      await activate(index, true);
      const step = (inner) => setProgress((index + inner) / total);
      step(0);
      try {
        if (state.kind === 'video') {
          const result = await convertVideo({
            file: state.file,
            video: els.media,
            width: format.width,
            height: format.height,
            paint: (ctx, W, H, source) => paintFrame(ctx, W, H, false, source),
            onProgress: step,
          });
          made.push(new File([result.blob], uniqueName(outputName(format, 'mp4'), taken),
            { type: 'video/mp4' }));
          if (!result.instagramReady) {
            notice(`この端末では ${result.codec} でしか書き出せませんでした。` +
              'Instagram が受け付けない場合があります');
          }
        } else {
          const url = renderTo(format).toDataURL('image/jpeg', 0.96);
          made.push(new File([dataUrlToBlob(url)], uniqueName(outputName(format), taken),
            { type: 'image/jpeg' }));
        }
      } catch (err) {
        failures.push(`${state.name}: ${(err && err.message) || err}`);
      }
      step(1);
    }
  } finally {
    if (wakeLock) wakeLock.release().catch(() => {});
    setProgress(-1);
    setBusy(false);
    await activate(startedAt);
  }

  if (!made.length) {
    notice(failures.length ? `書き出せませんでした（${failures[0]}）` : '書き出せませんでした');
    return;
  }

  state.batch = made;
  if (canShareFiles) {
    toast(`${made.length} 件を書き出しました。「まとめて共有」で保存できます`);
  } else {
    const entries = [];
    for (const file of made) entries.push({ name: file.name, data: await blobBytes(file) });
    const zip = zipStore(entries);
    download(new File([zip], zipName(), { type: 'application/zip' }));
    toast(`${made.length} 件を ZIP にまとめました`);
  }
  if (failures.length) notice(`${failures.length} 件は書き出せませんでした（${failures[0]}）`);
  refresh();
}

function zipName() {
  return `POP_${stamp()}_x${state.items.length}.zip`;
}

async function shareBatch() {
  if (!state.batch || !state.batch.length) return;
  if (canShareFiles && navigator.canShare({ files: state.batch })) {
    navigator.share({ files: state.batch, title: 'POP' }).catch(() => {});
    return;
  }
  const entries = [];
  for (const file of state.batch) entries.push({ name: file.name, data: await blobBytes(file) });
  download(new File([zipStore(entries)], zipName(), { type: 'application/zip' }));
}

function saveFormats(formats) {
  if (!state.srcW) return;
  if (state.kind === 'video') { exportVideo(); return; }
  const files = filesFor(formats);
  files.forEach(download);
  toast(files.length === 1 ? '保存しました' : `${files.length} 枚保存しました`);
}

function shareFormats(formats) {
  if (!state.srcW) return;
  if (state.kind === 'video') {
    // The file is already made: sharing has to happen inside this very click,
    // or Safari refuses it.
    if (state.made && canShareFiles) navigator.share({ files: [state.made], title: 'POP' }).catch(() => {});
    else if (state.made) download(state.made);
    else exportVideo();
    return;
  }
  const files = filesFor(formats);
  if (canShareFiles) {
    navigator.share({ files, title: 'POP' }).catch(() => {});
  } else {
    files.forEach(download);
  }
}

// ---- chrome ----------------------------------------------------------------

let toastTimer = null;
function toast(message) {
  els.toast.textContent = message;
  els.toast.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => els.toast.classList.remove('on'), 2200);
}

let problem = null;
function notice(message) {
  problem = message;
  refresh();
}

function setBusy(on) {
  state.busy = on;
  refresh();
}

function refresh() {
  const ready = !!state.srcW && !state.busy;
  const video = state.kind === 'video';
  if (state.progress >= 0) {
    els.subtitle.textContent = `書き出し中… ${Math.round(state.progress * 100)}%`;
  } else {
    els.subtitle.textContent = state.busy
      ? '読み込み中…'
      : (problem || state.name || `A4 の POP を投稿サイズに ・ ${BUILD}`);
  }
  els.subtitle.classList.toggle('warn', !!problem);

  for (const button of els.formats.children) {
    button.setAttribute('aria-pressed', String(state.format.key === button.dataset.format));
  }
  for (const button of els.modes.children) {
    button.setAttribute('aria-pressed', String(state.mode === button.dataset.mode));
  }
  for (const button of els.backdrops.children) {
    button.setAttribute('aria-pressed', String(state.backdrop === button.dataset.backdrop));
  }
  const dot = els.backdrops.querySelector('[data-dot="paper"]');
  if (dot) dot.style.background = state.paper;

  els.marginOut.textContent = `${Math.round(state.margin * 100)}%`;
  els.reset.disabled = !ready;
  els.saveAll.disabled = !ready || video;
  els.saveAll.textContent = video ? '動画は 1 サイズずつ' : '3サイズまとめて保存';
  els.save.disabled = !ready;
  els.share.disabled = !ready || (video && !state.made);
  els.share.hidden = !canShareFiles;
  els.phoneHint.hidden = !canShareFiles;
  els.save.textContent = video
    ? 'MP4 で書き出す'
    : (canShareFiles ? 'ダウンロード' : '保存');
  for (const button of [els.openPhoto, els.openPdf, els.openVideo,
    els.openPhotoBig, els.openPdfBig, els.openVideoBig]) {
    if (button) button.disabled = state.busy;
  }

  const several = state.items.length > 1;
  els.stripHead.hidden = !several;
  els.stripCount.textContent = `${state.items.length} 件（設定は全部に効きます）`;
  els.clear.disabled = state.busy;
  els.batchRow.hidden = !several;
  els.batch.disabled = !ready;
  els.batch.textContent = state.batch && canShareFiles
    ? `まとめて共有（${state.batch.length} 件）`
    : `${state.items.length} 件すべて書き出す`;

  const many = state.pdf && state.pageCount > 1;
  els.pages.hidden = !many;
  if (many) {
    els.pageLabel.textContent = `ページ ${state.pageIndex + 1} / ${state.pageCount}`;
    els.prevPage.disabled = state.busy || state.pageIndex === 0;
    els.nextPage.disabled = state.busy || state.pageIndex >= state.pageCount - 1;
  }
}

function setColorDots() {
  const colors = { white: '#ffffff', cream: CREAM, ink: INK };
  for (const [key, value] of Object.entries(colors)) {
    const dot = els.backdrops.querySelector(`[data-dot="${key}"]`);
    if (dot) dot.style.background = value;
  }
  const blur = els.backdrops.querySelector('[data-dot="blur"]');
  if (blur) blur.style.background = 'linear-gradient(135deg, #5e5e6b, #d8d5cc, #6e6a62)';
}

// ---- interaction -----------------------------------------------------------

function transform(dx, dy, zoomBy) {
  if (!state.srcW) return;
  const f = state.format;
  state.layout = normalize(
    { zoom: state.layout.zoom * zoomBy, x: state.layout.x + dx, y: state.layout.y + dy },
    state.srcW, state.srcH, f.width, f.height, state.margin, state.mode,
  );
  draw();
}

function resetPlacement() {
  state.layout = { zoom: 1, x: 0, y: 0 };
  draw();
}

function wireGestures() {
  const c = els.preview;
  const points = new Map();
  let gesture = null;

  const readout = () => {
    const list = [...points.values()];
    const cx = list.reduce((s, p) => s + p.x, 0) / list.length;
    const cy = list.reduce((s, p) => s + p.y, 0) / list.length;
    let dist = 0;
    if (list.length > 1) {
      dist = Math.hypot(list[0].x - list[1].x, list[0].y - list[1].y);
    }
    return { cx, cy, dist };
  };

  c.addEventListener('pointerdown', (e) => {
    c.setPointerCapture(e.pointerId);
    points.set(e.pointerId, { x: e.clientX, y: e.clientY });
    gesture = readout();
  });

  c.addEventListener('pointermove', (e) => {
    if (!points.has(e.pointerId)) return;
    e.preventDefault();
    points.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = readout();
    if (gesture) {
      const rect = c.getBoundingClientRect();
      const zoomBy = gesture.dist > 8 && now.dist > 8 ? now.dist / gesture.dist : 1;
      transform((now.cx - gesture.cx) / rect.width, (now.cy - gesture.cy) / rect.height, zoomBy);
    }
    gesture = now;
  });

  const release = (e) => {
    points.delete(e.pointerId);
    gesture = points.size ? readout() : null;
  };
  c.addEventListener('pointerup', release);
  c.addEventListener('pointercancel', release);

  c.addEventListener('wheel', (e) => {
    e.preventDefault();
    transform(0, 0, Math.exp(-e.deltaY * 0.0015));
  }, { passive: false });

  c.addEventListener('dblclick', resetPlacement);
}

function on(element, type, handler, options) {
  if (element) element.addEventListener(type, handler, options);
}

function wireControls() {
  const pickImage = () => { if (!state.busy) els.fileImage.click(); };
  const pickPdf = () => { if (!state.busy) els.filePdf.click(); };
  const pickVideo = () => { if (!state.busy) els.fileVideo.click(); };
  on(els.openPhoto, 'click', pickImage);
  on(els.openPhotoBig, 'click', pickImage);
  on(els.openPdf, 'click', pickPdf);
  on(els.openPdfBig, 'click', pickPdf);
  on(els.openVideo, 'click', pickVideo);
  on(els.openVideoBig, 'click', pickVideo);
  for (const input of [els.fileImage, els.filePdf, els.fileVideo]) {
    on(input, 'change', () => {
      const chosen = [...(input.files || [])];
      input.value = '';
      openFiles(chosen);
    });
  }

  els.formats.addEventListener('click', (e) => {
    const button = e.target.closest('[data-format]');
    if (!button) return;
    state.format = FORMATS[button.dataset.format];
    state.layout = { zoom: 1, x: 0, y: 0 };
    state.made = null;
    state.batch = null;
    if (state.srcW) {
      state.blurTile = rebuildTile(state.format.width / state.format.height, null);
    }
    refresh();
    fitPreview();
    draw();
  });

  els.modes.addEventListener('click', (e) => {
    const button = e.target.closest('[data-mode]');
    if (!button) return;
    state.mode = button.dataset.mode;
    state.layout = { zoom: 1, x: 0, y: 0 };
    state.made = null;
    state.batch = null;
    refresh();
    draw();
  });

  els.backdrops.addEventListener('click', (e) => {
    const button = e.target.closest('[data-backdrop]');
    if (!button) return;
    state.backdrop = button.dataset.backdrop;
    state.made = null;
    state.batch = null;
    refresh();
    draw();
  });

  els.margin.addEventListener('input', () => {
    state.margin = Number(els.margin.value) / 100;
    state.made = null;
    state.batch = null;
    if (state.srcW) {
      const f = state.format;
      state.layout = normalize(state.layout, state.srcW, state.srcH,
        f.width, f.height, state.margin, state.mode);
    }
    refresh();
    draw();
  });

  on(els.prevPage, 'click', () => turnPage(-1));
  on(els.nextPage, 'click', () => turnPage(1));

  on(els.clear, 'click', () => { if (!state.busy) clearAll(); });
  on(els.batch, 'click', () => {
    if (state.batch && canShareFiles) shareBatch();
    else exportAll();
  });
  on(els.reset, 'click', resetPlacement);
  on(els.save, 'click', () => saveFormats([state.format]));
  on(els.saveAll, 'click', () => saveFormats(Object.values(FORMATS)));
  on(els.share, 'click', () => shareFormats([state.format]));

  window.addEventListener('resize', () => { fitPreview(); draw(); });

  for (const type of ['dragenter', 'dragover']) {
    window.addEventListener(type, (e) => {
      e.preventDefault();
      document.body.classList.add('dropping');
    });
  }
  for (const type of ['dragleave', 'drop']) {
    window.addEventListener(type, (e) => {
      e.preventDefault();
      document.body.classList.remove('dropping');
    });
  }
  window.addEventListener('drop', (e) => {
    const dropped = [...((e.dataTransfer && e.dataTransfer.files) || [])];
    if (dropped.length) openFiles(dropped);
  });
  window.addEventListener('paste', (e) => {
    const pasted = [...((e.clipboardData && e.clipboardData.files) || [])];
    if (pasted.length) openFiles(pasted);
  });
}

try {
  setColorDots();
  wireControls();
  wireGestures();
  refresh();
} catch (err) {
  // A half-loaded page used to look like dead buttons; now it says so.
  const line = document.getElementById('subtitle');
  if (line) {
    line.textContent = `読み込みに失敗しました（${(err && err.message) || err}）。` +
      'ページを再読み込みしてください。';
    line.classList.add('warn');
  }
  throw err;
}
