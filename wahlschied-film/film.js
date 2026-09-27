// Wahlschied – virtual drone film.
// Real terrain (AWS Terrain Tiles), real aerial imagery (Esri World Imagery) and
// real building footprints (OpenStreetMap), rendered with three.js.
(async () => {
  const OUT_W = window.__RES ? window.__RES[0] : 1920, OUT_H = window.__RES ? window.__RES[1] : 1080;
  const CAPTURE = !!window.__CAPTURE;
  const $ = id => document.getElementById(id);

  // ---------- geo ----------
  const OLAT = 49.345, OLON = 7.014;
  const MX = 111320 * Math.cos(OLAT * Math.PI / 180), MZ = 110950;
  const L = (lat, lon) => ({ x: (lon - OLON) * MX, z: -(lat - OLAT) * MZ });
  const POI = {
    dorf: L(49.3432, 7.0012), evK: L(49.34361, 6.99842), willi: L(49.34414, 7.00103),
    heiden: L(49.34206, 6.99114), mast: L(49.34069, 7.0157), schacht: L(49.34357, 7.03874),
    kuehl: L(49.33413, 7.03135), kamin: L(49.33478, 7.03556), bunker: L(49.3478, 7.00581),
    westen: L(49.3425, 6.985)
  };

  // ---------- helpers ----------
  const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
  const lerp = (a, b, t) => a + (b - a) * t;
  const smooth = t => (t = clamp(t), t * t * (3 - 2 * t));
  const drift = t => 0.8 * t + 0.2 * smooth(t);
  const hex = c => new THREE.Color(c);

  const load = (u, type) => fetch(u).then(r => { if (!r.ok) throw new Error(u); return r[type](); });
  const bin = u => load(u, 'text').then(b => { const s = atob(b.trim()), a = new Uint8Array(s.length); for (let i = 0; i < s.length; i++) a[i] = s.charCodeAt(i); return a.buffer; });
  const [TL, meta, terrBuf, bld, treeBuf, farBuf] = await Promise.all([
    load('timeline.json', 'json'), load('meta.json', 'json'), bin('terrain.b64.txt'),
    load('buildings.json', 'json'), bin('trees.b64.txt'), bin('far.b64.txt')
  ]);

  // ---------- terrain sampler ----------
  const NX = meta.nx, NZ = meta.nz;
  const q = new Uint16Array(terrBuf);
  const HG = new Float32Array(q.length);
  for (let i = 0; i < q.length; i++) HG[i] = meta.hbase + q[i] * meta.hscale;
  const WX = meta.x1 - meta.x0, WZ = meta.z1 - meta.z0;
  function hAt(x, z) {
    const fx = clamp((x - meta.x0) / WX, 0, 1) * (NX - 1), fz = clamp((z - meta.z0) / WZ, 0, 1) * (NZ - 1);
    const ix = Math.min(NX - 2, Math.floor(fx)), iz = Math.min(NZ - 2, Math.floor(fz));
    const tx = fx - ix, tz = fz - iz, i = iz * NX + ix;
    return lerp(lerp(HG[i], HG[i + 1], tx), lerp(HG[i + NX], HG[i + NX + 1], tx), tz);
  }

  // ---------- renderer ----------
  const gl = document.createElement('canvas');
  gl.width = OUT_W; gl.height = OUT_H;
  const renderer = new THREE.WebGLRenderer({ canvas: gl, antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(OUT_W, OUT_H, false);
  renderer.outputEncoding = THREE.sRGBEncoding;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(50, OUT_W / OUT_H, 3, 40000);
  scene.fog = new THREE.Fog(0xcccccc, 1500, 11000);

  const hemi = new THREE.HemisphereLight(0xffffff, 0x445533, .7);
  const sun = new THREE.DirectionalLight(0xffffff, .8);
  scene.add(hemi, sun, sun.target);

  // sky dome
  const skyU = { top: { value: hex('#3d6fa8') }, hor: { value: hex('#cfdde6') }, sunDir: { value: new THREE.Vector3(0, 1, 0) }, sunCol: { value: hex('#fff') } };
  const sky = new THREE.Mesh(new THREE.SphereGeometry(30000, 32, 16), new THREE.ShaderMaterial({
    uniforms: skyU, side: THREE.BackSide, depthWrite: false, fog: false,
    vertexShader: 'varying vec3 vD; void main(){ vD = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: `uniform vec3 top; uniform vec3 hor; uniform vec3 sunDir; uniform vec3 sunCol; varying vec3 vD;
      void main(){ float h = clamp(vD.y,0.0,1.0); vec3 c = mix(hor, top, pow(h,0.55));
        float s = max(dot(normalize(vD), normalize(sunDir)),0.0);
        c += sunCol * (pow(s,900.0)*1.6 + pow(s,40.0)*0.35 + pow(s,6.0)*0.12);
        gl_FragColor = vec4(c,1.0); }`
  }));
  sky.renderOrder = -1;
  scene.add(sky);

  // ---------- terrain with aerial imagery (3x3 chunks) ----------
  const texLoader = new THREE.TextureLoader();
  const maxAniso = renderer.capabilities.getMaxAnisotropy();
  const orthoTex = [];
  await Promise.all([0, 1, 2].flatMap(j => [0, 1, 2].map(i => new Promise((res, rej) => {
    texLoader.load(`ortho_${i}_${j}.jpg`, t => {
      t.encoding = THREE.sRGBEncoding; t.anisotropy = maxAniso;
      orthoTex[j * 3 + i] = t; res();
    }, undefined, rej);
  }))));
  const chunkOf = (x, z) => [clamp(Math.floor((x - meta.x0) / WX * 3), 0, 2), clamp(Math.floor((z - meta.z0) / WZ * 3), 0, 2)];
  const chunkBox = (i, j) => ({ x0: meta.x0 + WX * i / 3, x1: meta.x0 + WX * (i + 1) / 3, z0: meta.z0 + WZ * j / 3, z1: meta.z0 + WZ * (j + 1) / 3 });
  const terrainMats = [];
  const SEG = CAPTURE ? 150 : 120;
  for (let j = 0; j < 3; j++) for (let i = 0; i < 3; i++) {
    const b = chunkBox(i, j);
    const nx = SEG, nz = Math.round(SEG * (b.z1 - b.z0) / (b.x1 - b.x0));
    const pos = new Float32Array((nx + 1) * (nz + 1) * 3), uv = new Float32Array((nx + 1) * (nz + 1) * 2);
    let k = 0, u = 0;
    for (let r = 0; r <= nz; r++) for (let c = 0; c <= nx; c++) {
      const x = lerp(b.x0, b.x1, c / nx), z = lerp(b.z0, b.z1, r / nz);
      pos[k++] = x; pos[k++] = hAt(x, z); pos[k++] = z;
      uv[u++] = c / nx; uv[u++] = 1 - r / nz;
    }
    const idx = [];
    for (let r = 0; r < nz; r++) for (let c = 0; c < nx; c++) {
      const a = r * (nx + 1) + c, d = a + nx + 1;
      idx.push(a, d, a + 1, a + 1, d, d + 1);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
    g.setIndex(idx);
    g.computeVertexNormals();
    const m = new THREE.MeshLambertMaterial({ map: orthoTex[j * 3 + i] });
    terrainMats.push(m);
    scene.add(new THREE.Mesh(g, m));
  }
  // surrounding 25 x 25 km at lower resolution, so the horizon shows the real hills
  const skirtMat = new THREE.MeshLambertMaterial({ color: 0xffffff });
  {
    const F = meta.far, n = F.n, fq = new Uint16Array(farBuf);
    const pos = new Float32Array(n * n * 3), uv = new Float32Array(n * n * 2);
    for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
      const x = lerp(F.x0, F.x1, c / (n - 1)), z = lerp(F.z0, F.z1, r / (n - 1)), i = r * n + c;
      const out = Math.max(meta.x0 - x, x - meta.x1, meta.z0 - z, z - meta.z1);
      const sink = out < 0 ? 12 : 12 * clamp(1 - out / 300);
      pos[i * 3] = x; pos[i * 3 + 1] = F.hbase + fq[i] * F.hscale - sink; pos[i * 3 + 2] = z;
      uv[i * 2] = c / (n - 1); uv[i * 2 + 1] = 1 - r / (n - 1);
    }
    const idx = [];
    for (let r = 0; r < n - 1; r++) for (let c = 0; c < n - 1; c++) { const a = r * n + c, d = a + n; idx.push(a, d, a + 1, a + 1, d, d + 1); }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
    g.setIndex(idx); g.computeVertexNormals();
    const t = await new Promise((res, rej) => texLoader.load('far.jpg', res, undefined, rej));
    t.encoding = THREE.sRGBEncoding; t.anisotropy = maxAniso;
    skirtMat.map = t; skirtMat.needsUpdate = true;
    terrainMats.push(skirtMat);
    scene.add(new THREE.Mesh(g, skirtMat));
  }

  // ---------- buildings (OSM footprints; roofs textured with the aerial image) ----------
  {
    const wallPos = [], wallCol = [];
    const roofPos = Array.from({ length: 9 }, () => []), roofUv = Array.from({ length: 9 }, () => []);
    const plaster = ['#d8d0c0', '#cfc4ae', '#c4b9a5', '#dcd6ca', '#bfb29a', '#b9b3a8', '#d2c5ab', '#a99d8c'].map(hex);
    let seed = 7;
    const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
    for (const b of bld) {
      const h = b[0];
      if (h > 100) continue; // cooling tower is modelled separately
      let pts = [];
      for (let i = 1; i < b.length; i += 2) pts.push([b[i], b[i + 1]]);
      if (pts.length < 3) continue;
      let area = 0;
      for (let i = 0; i < pts.length; i++) { const p = pts[i], q2 = pts[(i + 1) % pts.length]; area += p[0] * q2[1] - q2[0] * p[1]; }
      if (area < 0) pts.reverse();
      let lo = 1e9, hi = -1e9, cx = 0, cz = 0;
      for (const [x, z] of pts) { const e = hAt(x, z); lo = Math.min(lo, e); hi = Math.max(hi, e); cx += x; cz += z; }
      cx /= pts.length; cz /= pts.length;
      const base = lo - 1, top = lo + h + (hi - lo) * .6;
      const col = plaster[Math.floor(rnd() * plaster.length)].clone().convertSRGBToLinear();
      const wall = (ax, ay, az, bx, by, bz, cx2, cy, cz2, f) => { wallPos.push(ax, ay, az, bx, by, bz, cx2, cy, cz2); for (let k = 0; k < 3; k++) wallCol.push(col.r * f, col.g * f, col.b * f); };
      for (let i = 0; i < pts.length; i++) {
        const [x1, z1] = pts[i], [x2, z2] = pts[(i + 1) % pts.length];
        const shade = .8 + .2 * rnd();
        wall(x1, base, z1, x2, base, z2, x2, top, z2, shade * .8);
        wall(x1, base, z1, x2, top, z2, x1, top, z1, shade);
      }
      const [ci, cj] = chunkOf(cx, cz), cb = chunkBox(ci, cj), n = cj * 3 + ci;
      const roofTri = P => { for (const [x, y, z] of P) { roofPos[n].push(x, y, z); roofUv[n].push(clamp((x - cb.x0) / (cb.x1 - cb.x0)), clamp(1 - (z - cb.z0) / (cb.z1 - cb.z0))); } };
      // oriented bounding box along the longest edge
      let best = 0, ux = 1, uz = 0;
      for (let i = 0; i < pts.length; i++) { const [x1, z1] = pts[i], [x2, z2] = pts[(i + 1) % pts.length]; const d = Math.hypot(x2 - x1, z2 - z1); if (d > best) { best = d; ux = (x2 - x1) / d; uz = (z2 - z1) / d; } }
      let a0 = 1e9, a1 = -1e9, b0 = 1e9, b1 = -1e9;
      for (const [x, z] of pts) { const a = (x - cx) * ux + (z - cz) * uz, b2 = -(x - cx) * uz + (z - cz) * ux; a0 = Math.min(a0, a); a1 = Math.max(a1, a); b0 = Math.min(b0, b2); b1 = Math.max(b1, b2); }
      const la = a1 - a0, lb = b1 - b0, fill = Math.abs(area) / 2 / (la * lb);
      if (h < 14 && fill > .82 && Math.min(la, lb) > 4.5 && Math.min(la, lb) < 18 && Math.abs(area) / 2 < 450) {
        // gable roof with the ridge along the long side
        const P = (a, b2, y) => [cx + a * ux - b2 * uz, y, cz + a * uz + b2 * ux];
        const yT = top + Math.min(5, Math.min(la, lb) * .42), e = .5;
        const c1 = P(a0 - e, b0 - e, top), c2 = P(a1 + e, b0 - e, top), c3 = P(a1 + e, b1 + e, top), c4 = P(a0 - e, b1 + e, top);
        if (la >= lb) {
          const bm = (b0 + b1) / 2, r1 = P(a0 - e, bm, yT), r2 = P(a1 + e, bm, yT);
          roofTri([c1, c2, r2]); roofTri([c1, r2, r1]); roofTri([c3, c4, r1]); roofTri([c3, r1, r2]);
          wall(...P(a0, b0, top), ...P(a0, b1, top), ...P(a0, bm, yT), .9); wall(...P(a1, b0, top), ...P(a1, b1, top), ...P(a1, bm, yT), .9);
        } else {
          const am = (a0 + a1) / 2, r1 = P(am, b0 - e, yT), r2 = P(am, b1 + e, yT);
          roofTri([c1, r1, r2]); roofTri([c1, r2, c4]); roofTri([c2, c3, r2]); roofTri([c2, r2, r1]);
          wall(...P(a0, b0, top), ...P(a1, b0, top), ...P(am, b0, yT), .9); wall(...P(a0, b1, top), ...P(a1, b1, top), ...P(am, b1, yT), .9);
        }
      } else {
        const tris = THREE.ShapeUtils.triangulateShape(pts.map(p => new THREE.Vector2(p[0], p[1])), []);
        for (const t of tris) roofTri(t.map(k => [pts[k][0], top, pts[k][1]]));
      }
    }
    const wg = new THREE.BufferGeometry();
    wg.setAttribute('position', new THREE.Float32BufferAttribute(wallPos, 3));
    wg.setAttribute('color', new THREE.Float32BufferAttribute(wallCol, 3));
    wg.computeVertexNormals();
    scene.add(new THREE.Mesh(wg, new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide })));
    for (let n = 0; n < 9; n++) {
      if (!roofPos[n].length) continue;
      const rg = new THREE.BufferGeometry();
      rg.setAttribute('position', new THREE.Float32BufferAttribute(roofPos[n], 3));
      rg.setAttribute('uv', new THREE.Float32BufferAttribute(roofUv[n], 2));
      rg.computeVertexNormals();
      const m = new THREE.MeshLambertMaterial({ map: orthoTex[n], side: THREE.DoubleSide });
      terrainMats.push(m);
      scene.add(new THREE.Mesh(rg, m));
    }
  }

  // ---------- trees (placed from the forest canopy in the aerial image) ----------
  {
    const dv = new DataView(treeBuf), N = treeBuf.byteLength / 8;
    const groups = Array.from({ length: 9 }, () => []);
    for (let i = 0; i < N; i++) {
      const x = dv.getInt16(i * 8, true) / 10, z = dv.getInt16(i * 8 + 2, true) / 10, h = dv.getUint8(i * 8 + 4) / 10;
      const [ci, cj] = chunkOf(x, z);
      groups[cj * 3 + ci].push([x, z, h, dv.getUint8(i * 8 + 5), dv.getUint8(i * 8 + 6), dv.getUint8(i * 8 + 7)]);
    }
    const mat = new THREE.MeshLambertMaterial({ color: 0xffffff });
    const trunkMat = new THREE.MeshLambertMaterial({ color: 0x3a2e24 });
    const v3t = new THREE.Vector3();
    const m4 = new THREE.Matrix4(), c = new THREE.Color();
    groups.forEach((G, n) => {
      if (!G.length) return;
      const geo = new THREE.IcosahedronGeometry(1, 0);
      const np = geo.attributes.position, nn = geo.attributes.normal;
      for (let k = 0; k < np.count; k++) { v3t.set(np.getX(k), np.getY(k), np.getZ(k)).normalize(); nn.setXYZ(k, v3t.x, v3t.y, v3t.z); }
      const im = new THREE.InstancedMesh(geo, mat, G.length);
      const tg = new THREE.CylinderGeometry(.5, .7, 1, 5, 1, true); tg.translate(0, .5, 0);
      const trunks = new THREE.InstancedMesh(tg, trunkMat, G.length);
      let minx = 1e9, maxx = -1e9, minz = 1e9, maxz = -1e9;
      G.forEach(([x, z, h, r, g, b], i) => {
        const y = hAt(x, z);
        const w = h * (.2 + .08 * ((i * 7919) % 13) / 13);
        m4.makeRotationY(i * 2.39996).scale(new THREE.Vector3(w, h * .36, w)).setPosition(x, y + h * .6, z);
        im.setMatrixAt(i, m4);
        m4.makeScale(h * .03, h * .35, h * .03).setPosition(x, y - .5, z);
        trunks.setMatrixAt(i, m4);
        const lum = (r + g + b) / 3;
        c.setRGB(lerp(r, lum, .25) / 255 * 1.12, lerp(g, lum, .25) / 255 * 1.12, lerp(b, lum, .25) / 255 * 1.08);
        c.convertSRGBToLinear();
        im.setColorAt(i, c);
        minx = Math.min(minx, x); maxx = Math.max(maxx, x); minz = Math.min(minz, z); maxz = Math.max(maxz, z);
      });
      geo.boundingSphere = new THREE.Sphere(new THREE.Vector3((minx + maxx) / 2, 350, (minz + maxz) / 2), Math.hypot(maxx - minx, maxz - minz) / 2 + 60);
      tg.boundingSphere = geo.boundingSphere.clone();
      scene.add(im, trunks);
    });
  }

  // ---------- landmarks ----------
  function placed(obj, p) { obj.position.set(p.x, hAt(p.x, p.z), p.z); scene.add(obj); return obj; }
  const lights = [];
  {
    // transmitter mast, 208 m, red/white
    const g = new THREE.Group();
    for (let k = 0; k < 8; k++) {
      const s = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.4, 26, 6), new THREE.MeshLambertMaterial({ color: k % 2 ? 0xf2f2f2 : 0xd0352b }));
      s.position.y = 13 + k * 26; g.add(s);
    }
    const wireMat = new THREE.LineBasicMaterial({ color: 0x9aa0a4, transparent: true, opacity: .55 });
    for (const hh of [70, 140, 200]) for (let a = 0; a < 3; a++) {
      const ang = a * 2.094 + .4, d = hh * .7;
      const lg = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, hh, 0), new THREE.Vector3(Math.cos(ang) * d, hAt(POI.mast.x + Math.cos(ang) * d, POI.mast.z + Math.sin(ang) * d) - hAt(POI.mast.x, POI.mast.z), Math.sin(ang) * d)]);
      g.add(new THREE.Line(lg, wireMat));
    }
    for (const hh of [70, 140, 209]) {
      const l = new THREE.Mesh(new THREE.SphereGeometry(2.2, 8, 6), new THREE.MeshBasicMaterial({ color: 0xff3322, fog: false }));
      l.position.y = hh; g.add(l); lights.push(l);
    }
    placed(g, POI.mast);
    // bell tower of the Protestant church
    const tw = new THREE.Group(), stone = new THREE.MeshLambertMaterial({ color: new THREE.Color('#c9bda6').convertSRGBToLinear() });
    const tb = new THREE.Mesh(new THREE.BoxGeometry(7, 30, 7), stone); tb.position.y = 15; tw.add(tb);
    const sp = new THREE.Mesh(new THREE.ConeGeometry(5.4, 16, 4), new THREE.MeshLambertMaterial({ color: new THREE.Color('#4a4f55').convertSRGBToLinear() }));
    sp.position.y = 38; sp.rotation.y = Math.PI / 4; tw.add(sp);
    placed(tw, L(49.34361, 6.99861));
    // cooling tower of the Weiher power plant, 135 m
    const prof = [];
    for (let k = 0; k <= 12; k++) { const y = k / 12 * 135; const r = 30 + 18 * Math.pow((y - 100) / 100, 2); prof.push(new THREE.Vector2(r, y)); }
    placed(new THREE.Mesh(new THREE.LatheGeometry(prof, 40), new THREE.MeshLambertMaterial({ color: 0xb9b6ae, side: THREE.DoubleSide })), POI.kuehl);
    // chimney, 232 m
    const ch = new THREE.Group();
    const shaft = new THREE.Mesh(new THREE.CylinderGeometry(4, 7.5, 232, 16), new THREE.MeshLambertMaterial({ color: 0xc8c4bb }));
    shaft.position.y = 116; ch.add(shaft);
    for (const [y0, col] of [[214, 0xc8322a], [200, 0xf2f2f2], [186, 0xc8322a]]) {
      const band = new THREE.Mesh(new THREE.CylinderGeometry(4.6, 4.9, 14, 16), new THREE.MeshLambertMaterial({ color: col }));
      band.position.y = y0 + 7; ch.add(band);
    }
    placed(ch, POI.kamin);
    // headframe of Göttelborn shaft IV (white steel tower)
    const hf = new THREE.Group();
    const white = new THREE.MeshLambertMaterial({ color: 0xf0efe8 });
    const body = new THREE.Mesh(new THREE.BoxGeometry(22, 72, 22), white); body.position.y = 36; hf.add(body);
    const head = new THREE.Mesh(new THREE.BoxGeometry(28, 16, 26), white); head.position.y = 80; hf.add(head);
    const wheel = new THREE.Mesh(new THREE.CylinderGeometry(6, 6, 2, 20), new THREE.MeshLambertMaterial({ color: 0x505458 }));
    wheel.rotation.x = Math.PI / 2; wheel.position.set(0, 92, 0); hf.add(wheel);
    placed(hf, POI.schacht);
  }

  // ---------- looks (time of day) ----------
  const LOOK = {
    dawn:   { top: '#243556', hor: '#eaa57c', fog: '#c29c86', sunCol: '#ffb070', az: 80, el: 4, hemi: .5, dir: .75, tint: '#ffd6b4', grade: 'contrast(1.08) saturate(1.1)' },
    dawn2:  { top: '#35507c', hor: '#f0bf95', fog: '#cdb09a', sunCol: '#ffc58a', az: 85, el: 9, hemi: .6, dir: .85, tint: '#ffe3c8', grade: 'contrast(1.08) saturate(1.1)' },
    morning:{ top: '#3f6ea5', hor: '#dfe2dc', fog: '#c9cfcc', sunCol: '#fff0d6', az: 115, el: 26, hemi: .72, dir: .85, tint: '#fff4e4', grade: 'contrast(1.06) saturate(1.02) sepia(.18)' },
    day:    { top: '#3b6ca8', hor: '#d3e0e8', fog: '#c3d0d8', sunCol: '#fff6e4', az: 160, el: 42, hemi: .78, dir: .8, tint: '#ffffff', grade: 'contrast(1.07) saturate(1.1)' },
    mine:   { top: '#48678c', hor: '#cfd6da', fog: '#b8c0c4', sunCol: '#fff1dc', az: 190, el: 38, hemi: .72, dir: .8, tint: '#f2f2ee', grade: 'contrast(1.12) saturate(.85)' },
    grey:   { top: '#5d6b7a', hor: '#b3bcc2', fog: '#a3acb2', sunCol: '#dfe4e8', az: 220, el: 30, hemi: .78, dir: .35, tint: '#dde2e4', grade: 'contrast(1.05) saturate(.6)' },
    golden: { top: '#476c9c', hor: '#f3c893', fog: '#dcbb96', sunCol: '#ffbf6a', az: 255, el: 11, hemi: .6, dir: 1.0, tint: '#ffe4c2', grade: 'contrast(1.08) saturate(1.15)' },
    golden2:{ top: '#3e5a8a', hor: '#f4b27a', fog: '#d6a888', sunCol: '#ffa850', az: 262, el: 6, hemi: .52, dir: .95, tint: '#ffd6b0', grade: 'contrast(1.1) saturate(1.18)' },
    dusk:   { top: '#161f3d', hor: '#df7a50', fog: '#76647a', sunCol: '#ff8448', az: 275, el: 1, hemi: .36, dir: .55, tint: '#c7a8b0', grade: 'contrast(1.1) saturate(1.1)' }
  };
  function applyLook(a, b, t) {
    const A = LOOK[a], B = LOOK[b || a];
    const col = k => hex(A[k]).lerp(hex(B[k]), t);
    skyU.top.value.copy(col('top')); skyU.hor.value.copy(col('hor')); skyU.sunCol.value.copy(col('sunCol'));
    scene.fog.color.copy(col('fog'));
    const az = lerp(A.az, B.az, t) * Math.PI / 180, el = lerp(A.el, B.el, t) * Math.PI / 180;
    const d = new THREE.Vector3(Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el));
    skyU.sunDir.value.copy(d);
    sun.position.copy(camera.position).addScaledVector(d, 1000); sun.target.position.copy(camera.position);
    sun.color.copy(col('sunCol')); sun.intensity = lerp(A.dir, B.dir, t);
    hemi.color.copy(col('hor')); hemi.intensity = lerp(A.hemi, B.hemi, t);
    const tint = col('tint');
    terrainMats.forEach(m => m.color.copy(tint));
    return t < .5 ? A.grade : B.grade;
  }

  // ---------- camera choreography ----------
  const V = (x, y, z) => new THREE.Vector3(x, y, z);
  const at = (p, agl, dx = 0, dz = 0) => V(p.x + dx, hAt(p.x + dx, p.z + dz) + agl, p.z + dz);
  const path = (pts, t) => new THREE.CatmullRomCurve3(pts).getPoint(clamp(t));
  const SHOT = {
    intro:   { look: ['dawn', 'dawn2'], cam: t => ({ pos: path([at(POI.dorf, 230, 1500, 1350), at(POI.dorf, 190, 1000, 950), at(POI.dorf, 160, 450, 520)], drift(t)), tgt: at(POI.dorf, 0, -250, -120) }) },
    title:   { look: ['dawn2'], cam: t => { const a = lerp(.6, .95, t); return { pos: at(POI.dorf, lerp(260, 330, t), Math.cos(a) * 520, Math.sin(a) * 520), tgt: at(POI.dorf, 0) }; } },
    '1331':  { look: ['morning'], cam: t => ({ pos: path([at(POI.willi, 125, 560, 280), at(POI.willi, 110, 360, 160), at(POI.willi, 100, 230, 70)], drift(t)), tgt: at(POI.willi, 10, -120, -20) }) },
    kirche:  { look: ['morning', 'day'], cam: t => { const a = lerp(-.4, 1.2, drift(t)); return { pos: at(POI.willi, 95, Math.cos(a) * 210, Math.sin(a) * 210), tgt: at(POI.willi, 12) }; } },
    dorf:    { look: ['day'], cam: t => ({ pos: path([at(POI.evK, 95, 480, 330), at(POI.evK, 80, 300, 210), at(POI.evK, 70, 180, 120)], drift(t)), tgt: at(POI.evK, 20, -30, -10) }) },
    bergbau: { look: ['mine'], cam: t => ({ pos: path([at(POI.willi, 110, 100, 260), at(POI.mast, 200, -500, 150), at(POI.schacht, 240, -900, 150)], drift(t)), tgt: V(lerp(POI.kuehl.x, POI.schacht.x, smooth(t)) + 200, hAt(POI.schacht.x, POI.schacht.z) + 30, lerp(POI.kuehl.z, POI.schacht.z, smooth(t))) }) },
    mast:    { look: ['day'], cam: t => { const a = lerp(2.2, 3.9, drift(t)); const r = lerp(240, 190, t); const base = hAt(POI.mast.x, POI.mast.z); return { pos: V(POI.mast.x + Math.cos(a) * r, base + lerp(40, 250, smooth(t)), POI.mast.z + Math.sin(a) * r), tgt: V(POI.mast.x, base + lerp(90, 200, smooth(t)), POI.mast.z) }; } },
    heiden:  { look: ['grey'], cam: t => ({ pos: path([at(POI.heiden, 180, 280, 400), at(POI.heiden, 120, 180, 260), at(POI.heiden, 85, 120, 160)], drift(t)), tgt: at(POI.heiden, 0) }) },
    krieg:   { look: ['day', 'golden'], cam: t => ({ pos: path([at(POI.bunker, 60, -520, -420), at(POI.bunker, 55, -250, -170), at(POI.bunker, 80, 40, 60), at(POI.dorf, 150, 250, -150)], drift(t)), tgt: t < .55 ? at(POI.bunker, 0, 60, 60) : at(POI.dorf, 0) }) },
    '1974':  { look: ['golden'], cam: t => ({ pos: path([at(POI.willi, 90, 250, 120), at(POI.willi, 260, 700, 360), at(POI.willi, 420, 1150, 560)], drift(t)), tgt: at(POI.westen, 0, -300 * t, 0) }) },
    heute:   { look: ['golden', 'golden2'], cam: t => { const a = lerp(-.9, -.2, drift(t)); return { pos: at(POI.dorf, 330, Math.cos(a) * 780, Math.sin(a) * 780), tgt: at(POI.dorf, 0) }; } },
    ende:    { look: ['golden2', 'dusk'], cam: t => { const a = lerp(-.2, .35, t); return { pos: at(POI.dorf, lerp(260, 520, smooth(t)), Math.cos(a) * lerp(700, 1300, smooth(t)), Math.sin(a) * lerp(700, 1300, smooth(t))), tgt: at(POI.dorf, 0, -600, 0) }; } }
  };

  // ---------- on-screen text ----------
  const cues = [];
  const card = (id, phrase, big, small) => cues.push({ id, phrase, big, small, kind: 'card' });
  card('1331', 'dreizehnhunderteinunddreißig', '1331', 'Erste urkundliche Erwähnung');
  card('1331', 'Waldschid', 'Waldschid', 'Walscheidt · Walschit · Wallschied');
  card('1331', 'siebzehnhundertfünfunddreißig', '1735', 'Der Name „Wahlschied“ setzt sich durch');
  card('kirche', 'Ritter', '14. Jh.', 'Ritter von Dagstuhl · Deutscher Orden');
  card('kirche', 'Dreißigjährigen', '1618–1648', 'Zerstörung und Wiederaufbau');
  card('dorf', 'Fünfzehnhundertsechsundsiebzig', '1576', 'Wahlschied kommt zu Heusweiler');
  card('dorf', 'Siebzehnhundertfünfundzwanzig', '1725', 'Die erste Schule');
  card('dorf', 'neunzehnhunderteins', '1901', 'Bau der evangelischen Kirche');
  card('bergbau', 'Siebzehnhundertdreiundsiebzig', '1773', 'Wahlschied wird Bergbausiedlung');
  card('bergbau', 'Ein Flöz', 'Flöz Wahlschied', 'Ein Kohleflöz mit dem Namen des Dorfes');
  card('heiden', 'neunzehnhundertzweiundzwanzig', '1922', 'Der Heidenfriedhof wird angelegt');
  card('heiden', 'Zweitausendeinundzwanzig', '2021', 'Restaurierung');
  card('krieg', 'neunzehnhundertsiebenundzwanzig', '1927', 'Linienbus nach Saarbrücken');
  card('krieg', 'neunzehnhundertdreiundfünfzig', '1953', 'Oberleitungsbus Heusweiler – Holz');
  card('1974', 'Neunzehnhundertsechsundfünfzig', '1956', 'Neue katholische Kirche St. Willibrord');
  card('1974', 'ersten Januar', '1.1.1974', 'Teil der Gemeinde Heusweiler');
  card('heute', 'Heute', '1.478', 'Einwohner · 2,12 km² · 359 m ü. NN');
  const tags = {
    '1331': [['St. Willibrord', POI.willi, 25]], kirche: [['Kath. Kirche St. Willibrord', POI.willi, 28]],
    dorf: [['Evangelische Kirche', POI.evK, 30]],
    bergbau: [['Göttelborn · Schacht IV', POI.schacht, 100], ['Kraftwerk Weiher', POI.kuehl, 150], ['Sendemast', POI.mast, 215]],
    mast: [['Sender Göttelborner Höhe · 208 m', POI.mast, 212]], heiden: [['Heidenfriedhof', POI.heiden, 6]],
    krieg: [['Westwall-Bunker', POI.bunker, 4]], '1974': [['Richtung Heusweiler', POI.westen, 30]], heute: [['Wahlschied', POI.dorf, 30]]
  };
  const shots = TL.shots.map(s => ({ ...s, ...SHOT[s.id] }));
  // place cards at the moment the phrase is spoken (by character position in the narration)
  for (const c of cues) {
    const s = shots.find(x => x.id === c.id);
    const i = s.say.indexOf(c.phrase);
    c.t0 = s.vo[0] + (s.vo[1] - s.vo[0]) * Math.max(0, i) / s.say.length - .2;
  }
  cues.sort((a, b) => a.t0 - b.t0);
  cues.forEach((c, i) => {
    const s = shots.find(x => x.id === c.id);
    const next = cues[i + 1] && cues[i + 1].id === c.id ? cues[i + 1].t0 - .2 : s.start + s.dur - .4;
    c.t1 = Math.min(next, c.t0 + 6);
  });

  // ---------- output canvas (grade, letterbox, text) ----------
  const out = $('screen');
  out.width = OUT_W; out.height = OUT_H;
  const ctx = out.getContext('2d');
  const BAR = Math.round((OUT_H - OUT_W / 2.39) / 2);
  const F = s => Math.round(s * OUT_H / 1080);

  function wrap(text, maxW) {
    const words = text.split(' '), lines = [];
    let line = '';
    for (const w of words) { const test = line ? line + ' ' + w : w; if (ctx.measureText(test).width > maxW && line) { lines.push(line); line = w; } else line = test; }
    if (line) lines.push(line);
    return lines;
  }

  function drawCard(c, t) {
    const a = smooth((t - c.t0) / .5) * (1 - smooth((t - c.t1 + .5) / .5));
    if (a <= 0) return;
    const x = F(96), y = BAR + F(92), rise = (1 - a) * F(14);
    ctx.save();
    ctx.globalAlpha = a;
    const bg = ctx.createLinearGradient(0, 0, F(760), 0);
    bg.addColorStop(0, 'rgba(8,10,9,.5)'); bg.addColorStop(1, 'rgba(8,10,9,0)');
    ctx.fillStyle = bg; ctx.fillRect(0, y - F(30), F(760), F(180));
    ctx.shadowColor = 'rgba(0,0,0,.7)'; ctx.shadowBlur = F(24);
    ctx.fillStyle = '#d9a441';
    ctx.fillRect(x, y + rise - F(4), F(3), F(118));
    ctx.textAlign = 'left';
    ctx.fillStyle = '#fbf5e6';
    ctx.font = `800 ${F(c.big.length > 9 ? 64 : 84)}px "Alegreya SC", Georgia, serif`;
    ctx.textBaseline = 'top';
    ctx.fillText(c.big, x + F(26), y + rise);
    ctx.font = `italic ${F(34)}px Alegreya, Georgia, serif`;
    ctx.fillStyle = '#f0e2c0';
    ctx.fillText(c.small, x + F(28), y + rise + F(c.big.length > 9 ? 76 : 94));
    ctx.restore();
  }

  const v3 = new THREE.Vector3();
  function drawTag(txt, p, hOff, a) {
    v3.set(p.x, hAt(p.x, p.z) + hOff, p.z).project(camera);
    if (v3.z > 1 || Math.abs(v3.x) > 1.1 || Math.abs(v3.y) > 1.1) return;
    const sx = (v3.x + 1) / 2 * OUT_W, sy = (1 - v3.y) / 2 * OUT_H;
    const ly = Math.max(BAR + F(70), sy - F(110));
    ctx.save();
    ctx.globalAlpha = a;
    ctx.strokeStyle = 'rgba(255,248,230,.9)'; ctx.lineWidth = F(2);
    ctx.beginPath(); ctx.moveTo(sx, sy); ctx.lineTo(sx, ly + F(8)); ctx.stroke();
    ctx.fillStyle = '#fff8e6';
    ctx.beginPath(); ctx.arc(sx, sy, F(6), 0, 7); ctx.fill();
    ctx.font = `700 ${F(28)}px Karla, system-ui, sans-serif`;
    const w = ctx.measureText(txt).width;
    const lx = clamp(sx, w / 2 + F(40), OUT_W - w / 2 - F(40));
    ctx.fillStyle = 'rgba(14,18,16,.62)';
    ctx.fillRect(lx - w / 2 - F(16), ly - F(40), w + F(32), F(50));
    ctx.fillStyle = '#d9a441'; ctx.fillRect(lx - w / 2 - F(16), ly + F(7), w + F(32), F(3));
    ctx.fillStyle = '#fbf5e6'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(txt, lx, ly - F(15));
    ctx.restore();
  }

  function titleCard(big, small, a, y = OUT_H * .44) {
    if (a <= 0) return;
    ctx.save();
    ctx.globalAlpha = a;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.shadowColor = 'rgba(0,0,0,.6)'; ctx.shadowBlur = F(40);
    ctx.fillStyle = '#fbf5e6';
    ctx.font = `800 ${F(150)}px "Alegreya SC", Georgia, serif`;
    if ('letterSpacing' in ctx) ctx.letterSpacing = `${F(14)}px`;
    ctx.fillText(big, OUT_W / 2 + F(7), y);
    if ('letterSpacing' in ctx) ctx.letterSpacing = '0px';
    ctx.font = `italic ${F(40)}px Alegreya, Georgia, serif`;
    ctx.fillStyle = '#f2d9a4';
    ctx.fillText(small, OUT_W / 2, y + F(108));
    ctx.restore();
  }

  function frame(t) {
    t = clamp(t, 0, TL.total - .001);
    const si = Math.max(0, shots.findIndex(s => t < s.start + s.dur));
    const s = shots[si], p = (t - s.start) / s.dur;
    const cam = s.cam(p);
    camera.position.copy(cam.pos);
    camera.lookAt(cam.tgt);
    const grade = applyLook(s.look[0], s.look[1], smooth(p));
    const blink = Math.sin(t * 5) > 0;
    lights.forEach(l => l.visible = blink);
    renderer.render(scene, camera);

    ctx.save();
    ctx.filter = grade;
    ctx.drawImage(gl, 0, 0, OUT_W, OUT_H);
    ctx.restore();
    // vignette
    const vg = ctx.createRadialGradient(OUT_W / 2, OUT_H / 2, OUT_H * .45, OUT_W / 2, OUT_H / 2, OUT_H * 1.05);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, 'rgba(0,0,0,.42)');
    ctx.fillStyle = vg; ctx.fillRect(0, 0, OUT_W, OUT_H);

    // place tags
    for (const [txt, pt, hh] of tags[s.id] || []) {
      const a = smooth((p - .08) / .1) * (1 - smooth((p - .86) / .1));
      if (a > 0) drawTag(txt, pt, hh, a);
    }
    // year cards
    cues.forEach(c => { if (t > c.t0 - .1 && t < c.t1 + .1) drawCard(c, t); });
    // titles
    if (s.id === 'intro') titleCard('WAHLSCHIED', 'Ein Flug über den ältesten Ort des Köllertals', smooth((p - .82) / .12));
    if (s.id === 'title') titleCard('WAHLSCHIED', 'Saarland · Köllertal · seit 1331', 1 - smooth((p - .7) / .25));
    if (s.id === 'ende') titleCard('WAHLSCHIED', 'seit 1331', smooth((p - .25) / .2), OUT_H * .42);

    // cuts: short dip to black; long fades at start and end
    const edge = Math.min(t - s.start, s.start + s.dur - t);
    let black = 0;
    if (si > 0 && t - s.start < .35) black = 1 - (t - s.start) / .35;
    if (si < shots.length - 1 && s.start + s.dur - t < .35) black = Math.max(black, 1 - (s.start + s.dur - t) / .35);
    if (s.id === 'title' && t - s.start < .35) black = 0;
    if (s.id === 'intro' && s.start + s.dur - t < .35) black = 0;
    if (t < 2) black = Math.max(black, 1 - t / 2);
    if (t > TL.total - 3) black = Math.max(black, (t - (TL.total - 3)) / 3);
    if (black > 0) { ctx.fillStyle = `rgba(0,0,0,${clamp(black)})`; ctx.fillRect(0, 0, OUT_W, OUT_H); }

    // cinema bars with subtitles
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, OUT_W, BAR); ctx.fillRect(0, OUT_H - BAR, OUT_W, BAR);
    if (window.__SUBS !== false && s.sub && t >= s.vo[0] - .3 && t <= s.vo[1] + .8) {
      ctx.font = `500 ${F(30)}px Karla, system-ui, sans-serif`;
      ctx.fillStyle = 'rgba(245,238,222,.92)';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      // show the sentence being spoken
      const split = x => x.match(/.+?[.!?:](?=\s|$)/g) || [x];
      const sentences = split(s.sub), spoken = split(s.say);
      const timing = spoken.length === sentences.length ? spoken : sentences;
      const total = timing.reduce((a, b) => a + b.length, 0); let acc = 0, cur = sentences[0];
      const prog = clamp((t - s.vo[0]) / (s.vo[1] - s.vo[0]));
      timing.forEach((sn, i) => { if (prog * total >= acc - 2) cur = sentences[i]; acc += sn.length; });
      const lines = wrap(cur.trim(), OUT_W * .8).slice(0, 2);
      lines.forEach((ln, i) => ctx.fillText(ln, OUT_W / 2, OUT_H - BAR / 2 + (i - (lines.length - 1) / 2) * F(38)));
    }
    return s;
  }

  window.__frame = frame;
  window.__total = TL.total;
  window.__shots = shots;
  window.__ready = true;
  if (window.__onReady) window.__onReady();
})().catch(e => { window.__error = String(e && e.stack || e); console.error(e); });
