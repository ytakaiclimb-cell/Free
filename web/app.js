// POP → INSTAGRAM, the browser edition of the Android app in this repo.
// Same geometry, same backdrops, same output: a 1080 px wide post.

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

const state = {
  source: null,     // full resolution canvas
  preview: null,    // smaller copy, redrawn on every gesture
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
};

// Stamped by the publishing workflow; "dev" when served straight from disk.
const rawBuild = document.body.getAttribute('data-build') || '';
const BUILD = /^[0-9]/.test(rawBuild) ? rawBuild : 'dev';

const els = {};
for (const id of ['subtitle', 'openPhoto', 'openPdf', 'openPhotoBig', 'openPdfBig',
  'fileImage', 'filePdf', 'stage', 'empty', 'preview', 'formats',
  'modes', 'backdrops', 'margin', 'marginOut', 'pages', 'pageLabel', 'prevPage', 'nextPage',
  'reset', 'saveAll', 'save', 'share', 'toast']) {
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
function blurTile(src, aspect) {
  const w = 48;
  const h = Math.max(1, Math.round(w / aspect));
  const c = canvasOf(w, h);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#000';
  ctx.fillRect(0, 0, w, h);
  const small = reduce(src, w * 6);
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
  ctx.drawImage(small, sx, sy, sw, sh, 0, 0, w, h);
  return c;
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
  if (!state.source) return;
  const c = els.preview;
  const ctx = c.getContext('2d');
  const p = place(state.layout, state.source.width, state.source.height,
    c.width, c.height, state.margin, state.mode);
  compose(ctx, c.width, c.height, state.preview, state.blurTile, p);
}

function fitPreview() {
  if (!state.source) return;
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
  const tile = state.backdrop === 'blur'
    ? blurTile(state.source, format.width / format.height)
    : null;
  const p = place(state.layout, state.source.width, state.source.height,
    format.width, format.height, state.margin, state.mode);
  const src = reduce(state.source, Math.round(p.width));
  compose(ctx, format.width, format.height, src, tile, p);
  return c;
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

async function loadPdf(file) {
  const lib = await pdfLib();
  const data = new Uint8Array(await file.arrayBuffer());
  const doc = await lib.getDocument({
    data,
    cMapUrl: new URL('./vendor/cmaps/', import.meta.url).href,
    cMapPacked: true,
  }).promise;
  state.pdf = doc;
  state.pageCount = doc.numPages;
  state.name = file.name;
  await showPdfPage(0);
}

async function showPdfPage(index) {
  const doc = state.pdf;
  if (!doc) return;
  const page = await doc.getPage(index + 1);
  const unit = page.getViewport({ scale: 1 });
  const scale = MAX_EDGE / Math.max(unit.width, unit.height);
  const viewport = page.getViewport({ scale });
  const c = canvasOf(viewport.width, viewport.height);
  const ctx = c.getContext('2d');
  ctx.fillStyle = '#ffffff';       // a PDF paints nothing where the paper is blank
  ctx.fillRect(0, 0, c.width, c.height);
  await page.render({ canvasContext: ctx, viewport }).promise;
  state.pageIndex = index;
  adopt(c, state.name);
}

function adopt(canvas, name) {
  state.source = canvas;
  state.preview = reduce(canvas, PREVIEW_EDGE);
  state.paper = paperColor(canvas);
  state.blurTile = blurTile(canvas, state.format.width / state.format.height);
  state.layout = { zoom: 1, x: 0, y: 0 };
  state.name = name || state.name;
  els.preview.hidden = false;
  els.empty.hidden = true;
  refresh();
  fitPreview();
  draw();
}

async function openFile(file) {
  if (!file || state.busy) return;
  setBusy(true);
  notice(null);
  try {
    const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    if (isPdf) {
      await loadPdf(file);
    } else {
      state.pdf = null;
      state.pageCount = 1;
      state.pageIndex = 0;
      state.name = file.name;
      adopt(cap(await decodeImage(file)), file.name);
    }
  } catch (err) {
    notice(`読み込めませんでした（${(err && err.message) || err}）`);
  } finally {
    setBusy(false);
  }
}

// ---- output ----------------------------------------------------------------

function outputName(format) {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  const stamp = `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}` +
    `_${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
  return `POP_${stamp}_${format.width}x${format.height}.jpg`;
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

function saveFormats(formats) {
  if (!state.source) return;
  const files = filesFor(formats);
  files.forEach(download);
  toast(files.length === 1 ? '保存しました' : `${files.length} 枚保存しました`);
}

function shareFormats(formats) {
  if (!state.source) return;
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
  const ready = !!state.source && !state.busy;
  els.subtitle.textContent = state.busy
    ? '読み込み中…'
    : (problem || state.name || `A4 の POP を投稿サイズに ・ ${BUILD}`);
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
  els.saveAll.disabled = !ready;
  els.save.disabled = !ready;
  els.share.disabled = !ready;
  els.share.hidden = !canShareFiles;
  for (const button of [els.openPhoto, els.openPdf, els.openPhotoBig, els.openPdfBig]) {
    if (button) button.disabled = state.busy;
  }

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
  if (!state.source) return;
  const f = state.format;
  state.layout = normalize(
    { zoom: state.layout.zoom * zoomBy, x: state.layout.x + dx, y: state.layout.y + dy },
    state.source.width, state.source.height, f.width, f.height, state.margin, state.mode,
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
  on(els.openPhoto, 'click', pickImage);
  on(els.openPhotoBig, 'click', pickImage);
  on(els.openPdf, 'click', pickPdf);
  on(els.openPdfBig, 'click', pickPdf);
  for (const input of [els.fileImage, els.filePdf]) {
    on(input, 'change', () => {
      const file = input.files && input.files[0];
      input.value = '';
      openFile(file);
    });
  }

  els.formats.addEventListener('click', (e) => {
    const button = e.target.closest('[data-format]');
    if (!button) return;
    state.format = FORMATS[button.dataset.format];
    state.layout = { zoom: 1, x: 0, y: 0 };
    if (state.source) {
      state.blurTile = blurTile(state.source, state.format.width / state.format.height);
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
    refresh();
    draw();
  });

  els.backdrops.addEventListener('click', (e) => {
    const button = e.target.closest('[data-backdrop]');
    if (!button) return;
    state.backdrop = button.dataset.backdrop;
    refresh();
    draw();
  });

  els.margin.addEventListener('input', () => {
    state.margin = Number(els.margin.value) / 100;
    if (state.source) {
      const f = state.format;
      state.layout = normalize(state.layout, state.source.width, state.source.height,
        f.width, f.height, state.margin, state.mode);
    }
    refresh();
    draw();
  });

  els.prevPage.addEventListener('click', async () => {
    if (state.busy || state.pageIndex === 0) return;
    setBusy(true);
    try { await showPdfPage(state.pageIndex - 1); } finally { setBusy(false); }
  });
  els.nextPage.addEventListener('click', async () => {
    if (state.busy || state.pageIndex >= state.pageCount - 1) return;
    setBusy(true);
    try { await showPdfPage(state.pageIndex + 1); } finally { setBusy(false); }
  });

  els.reset.addEventListener('click', resetPlacement);
  els.save.addEventListener('click', () => saveFormats([state.format]));
  els.saveAll.addEventListener('click', () => saveFormats(Object.values(FORMATS)));
  els.share.addEventListener('click', () => shareFormats([state.format]));

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
    const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (file) openFile(file);
  });
  window.addEventListener('paste', (e) => {
    const item = [...((e.clipboardData && e.clipboardData.files) || [])][0];
    if (item) openFile(item);
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
