// usage: node render.js <worker> <nworkers> <fps> <outdir>
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [w, nw, fps, dir] = [+process.argv[2], +process.argv[3], +process.argv[4], process.argv[5]];
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium', args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const pg = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  pg.on('pageerror', e => console.log('pageerror:', e.message));
  await pg.goto('http://127.0.0.1:8765/capture.html');
  await pg.waitForFunction(() => window.__ready || window.__error, null, { timeout: 300000 });
  await pg.evaluate(() => document.fonts.ready);
  const total = await pg.evaluate(() => window.__total);
  const N = Math.floor(total * fps), lim = +(process.env.LIMIT || N);
  let done = 0; const t0 = Date.now();
  for (let f = w; f < Math.min(N, lim); f += nw) {
    const fn = `${dir}/f${String(f).padStart(5, '0')}.jpg`;
    if (fs.existsSync(fn)) continue;
    const d = await pg.evaluate(t => { window.__frame(t); return document.getElementById('screen').toDataURL('image/jpeg', .92); }, f / fps);
    fs.writeFileSync(fn + '.tmp', Buffer.from(d.split(',')[1], 'base64')); fs.renameSync(fn + '.tmp', fn);
    if (++done % 50 === 0) console.log(`w${w} ${done} frames, ${((Date.now() - t0) / done / 1000).toFixed(2)} s/frame`);
  }
  console.log(`w${w} finished ${done}`);
  await b.close();
})();
