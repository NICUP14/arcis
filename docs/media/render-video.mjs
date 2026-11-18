// Renders the concept video (src/video.html) into demo.mp4 frame by frame with headless Microsoft Edge,
// then joins the frames into an MP4 with ffmpeg.
//
//   node docs/media/render-video.mjs [out.mp4] [--fps 30] [--fresh]
//
// Needs Node 22+ (built-in WebSocket) and Edge. ffmpeg (on PATH or installed with winget) makes
// the MP4; without it the frames are kept and the ffmpeg command is printed.
// Frames already rendered are kept, so an interrupted run carries on where it stopped; --fresh
// starts over (do that after changing video.html).
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const fpsAt = args.indexOf('--fps');
const fps = fpsAt >= 0 ? Number(args[fpsAt + 1]) : 30;
const fresh = args.includes('--fresh');
const out = resolve(args.find((a, i) => !a.startsWith('--') && i !== fpsAt + 1) ?? join(here, 'demo.mp4'));
const W = 1920, H = 1080, PORT = 9339, STEP_TIMEOUT_MS = 30_000;

const edge = [
  join(process.env['ProgramFiles(x86)'] ?? '', 'Microsoft/Edge/Application/msedge.exe'),
  join(process.env.ProgramFiles ?? '', 'Microsoft/Edge/Application/msedge.exe'),
].find(existsSync);
if (!edge) throw new Error('Microsoft Edge not found');

const frames = join(tmpdir(), 'arcis-video-frames');
if (fresh) rmSync(frames, { recursive: true, force: true });
mkdirSync(frames, { recursive: true });
const profile = join(tmpdir(), 'arcis-video-profile');
const frameFile = (f) => join(frames, String(f).padStart(5, '0') + '.png');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// One headless Edge with the video loaded; `send` is a DevTools call that fails after a timeout.
async function openPage() {
  const browser = spawn(edge, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
    '--allow-file-access-from-files', `--user-data-dir=${profile}`, `--remote-debugging-port=${PORT}`,
    `--window-size=${W},${H}`, 'about:blank',
  ], { stdio: 'ignore' });
  const close = () => { try { ws?.close(); } catch { /* already closed */ } browser.kill(); };

  let target, ws;
  for (let i = 0; i < 100 && !target; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      target = list.find((t) => t.type === 'page');
    } catch { /* not up yet */ }
    if (!target) await sleep(100);
  }
  if (!target) { close(); throw new Error('Edge did not start'); }

  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
  let nextId = 1;
  const pending = new Map();
  const events = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) {
      const { res, rej, timer } = pending.get(msg.id);
      pending.delete(msg.id);
      clearTimeout(timer);
      msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
    } else if (msg.method) events.push(msg.method);
  };
  const send = (method, params = {}) => new Promise((res, rej) => {
    const id = nextId++;
    const timer = setTimeout(() => { pending.delete(id); rej(new Error(`${method} timed out`)); }, STEP_TIMEOUT_MS);
    pending.set(id, { res, rej, timer });
    ws.send(JSON.stringify({ id, method, params }));
  });

  try {
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false });
    await send('Page.navigate', { url: pathToFileURL(join(here, 'src', 'video.html')).href });
    for (let i = 0; i < 100 && !events.includes('Page.loadEventFired'); i++) await sleep(50);
    await send('Runtime.evaluate', { expression: 'window.ready.then(() => true)', awaitPromise: true });
  } catch (e) { close(); throw e; }
  return { send, close };
}

let page = await openPage();
const { result } = await page.send('Runtime.evaluate', { expression: 'window.DURATION', returnByValue: true });
const total = Math.round(result.value * fps);

const started = Date.now();
let rendered = 0;
for (let f = 0; f < total; f++) {
  if (existsSync(frameFile(f)) && statSync(frameFile(f)).size > 0) continue;
  for (let attempt = 1; ; attempt++) {
    try {
      await page.send('Runtime.evaluate', { expression: `render(${(f / fps).toFixed(4)})` });
      const shot = await page.send('Page.captureScreenshot', { format: 'png' });
      writeFileSync(frameFile(f), Buffer.from(shot.data, 'base64'));
      break;
    } catch (e) {
      if (attempt >= 3) { page.close(); throw new Error(`frame ${f}: ${e.message}`); }
      process.stdout.write(`\nframe ${f}: ${e.message}, restarting Edge\n`);
      page.close();
      await sleep(1000);
      page = await openPage();
    }
  }
  rendered++;
  if (f % 10 === 0) process.stdout.write(`\rframe ${f}/${total}`);
}
page.close();
const done = readdirSync(frames).filter((n) => n.endsWith('.png')).length;
console.log(`\r${rendered} frames rendered in ${((Date.now() - started) / 1000).toFixed(0)} s; ${done}/${total} on disk`);

// ffmpeg from PATH, or where winget puts it (PATH only updates in new terminals).
function findFfmpeg() {
  if (spawnSync('ffmpeg', ['-version'], { stdio: 'ignore' }).status === 0) return 'ffmpeg';
  const pkgs = join(process.env.LOCALAPPDATA ?? '', 'Microsoft/WinGet/Packages');
  if (!existsSync(pkgs)) return null;
  for (const pkg of readdirSync(pkgs).filter((n) => n.toLowerCase().includes('ffmpeg'))) {
    for (const build of readdirSync(join(pkgs, pkg))) {
      const exe = join(pkgs, pkg, build, 'bin', 'ffmpeg.exe');
      if (existsSync(exe)) return exe;
    }
  }
  return null;
}

const ffmpegArgs = ['-y', '-framerate', String(fps), '-i', join(frames, '%05d.png'),
  '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18', '-movflags', '+faststart', out];
const ffmpeg = findFfmpeg();
if (ffmpeg) {
  mkdirSync(dirname(out), { recursive: true });
  const r = spawnSync(ffmpeg, ['-loglevel', 'error', ...ffmpegArgs], { stdio: ['ignore', 'ignore', 'inherit'] });
  if (r.status !== 0) throw new Error('ffmpeg failed');
  console.log(`video: ${out}`);
} else {
  console.log(`ffmpeg not found. Frames are in ${frames}; make the video with:\n  ffmpeg ${ffmpegArgs.join(' ')}`);
}
