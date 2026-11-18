// Interface: top bar and time controls, population panel, inspect panel, event strip, overlays, start screen, minimap.
import * as THREE from 'three';
import { CONFIG } from '../config.js';

const SPECIES_HEX = CONFIG.speciesColors.map((c) => '#' + c.toString(16).padStart(6, '0'));
const ACTIONS = ['-', 'DRINK', 'SEEK_WATER', 'HUNT', 'GRAZE', 'REST', 'BREED', 'MOVE', 'WAIT'];
const REASONS = ['-', 'CRITICAL_DEHYDRATION', 'WATER_DEPARTURE', 'CRITICAL_FOOD', 'THREAT_AVOIDANCE', 'EGRESS', 'THIRST', 'FOOD', 'REST', 'ILLNESS_RECOVERY', 'BLOCKED', 'BREEDING', 'ORDINARY_MOVEMENT'];
const STAGES = ['cub', 'juvenile', 'adult'];
const CAUSES = ['-', 'age', 'starvation', 'dehydration', 'disease', 'predation', 'position conflict', 'unknown'];
const WEATHER = ['clear', 'rain', 'fog', 'drought'];
const SPEEDS = [0.5, 1, 2, 4, 8, 0];

function el(tag, props = {}, parent) {
  const e = document.createElement(tag); Object.assign(e, props); if (parent) parent.appendChild(e); return e;
}

export class UI {
  constructor(app) {
    this.app = app; this.world = null; this.series = [[], [], [], [], []]; this.events = []; this.selected = null; this.follow = false;
    this.overlay = 'none'; this.lastFrameAt = performance.now(); this.index = new Map();
    this.root = el('div', { id: 'ui' }, document.body);
    this.buildTopBar(); this.buildPopulation(); this.buildInspect(); this.buildEvents(); this.buildMinimap(); this.buildStart();
    app.renderer.domElement.addEventListener('pointerdown', (e) => { this.downAt = [e.clientX, e.clientY]; });
    app.renderer.domElement.addEventListener('pointerup', (e) => {
      if (this.downAt && Math.hypot(e.clientX - this.downAt[0], e.clientY - this.downAt[1]) < 5) this.pick(e.clientX, e.clientY);
    });
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;
      if (e.key === ' ') { e.preventDefault(); this.togglePause(); }
      else if (e.key === 'f') this.toggleFollow();
      else if (e.key === '.') app.client.control({ cmd: 'step' });
    });
  }

  buildTopBar() {
    const bar = el('div', { id: 'topbar' }, this.root);
    this.clock = el('span', { id: 'clock', textContent: 'connecting…' }, bar);
    this.pauseButton = el('button', { textContent: 'Pause', title: 'Space' }, bar);
    this.pauseButton.onclick = () => this.togglePause();
    el('button', { textContent: 'Step', title: '.' }, bar).onclick = () => this.app.client.control({ cmd: 'step' });
    this.speedSelect = el('select', {}, bar);
    for (const s of SPEEDS) el('option', { value: s, textContent: s === 0 ? 'max' : s + '×', selected: s === 1 }, this.speedSelect);
    this.speedSelect.onchange = () => { const v = parseFloat(this.speedSelect.value); this.app.setSpeed(v === 0 ? 0 : v); };
    this.overlaySelect = el('select', { title: 'Overlay' }, bar);
    for (const [v, t] of [['none', 'overlay: off'], ['grass', 'grass'], ['density', 'animal density'], ['hotspot', 'prey hotspots']]) el('option', { value: v, textContent: t }, this.overlaySelect);
    this.overlaySelect.onchange = () => { this.overlay = this.overlaySelect.value; this.applyOverlay(); };
    this.waiting = el('span', { id: 'waiting', textContent: '' }, bar);
    el('button', { textContent: 'New run' }, bar).onclick = () => { this.start.style.display = 'flex'; };
  }

  buildPopulation() {
    this.pop = el('div', { id: 'pop' }, this.root);
    this.popRows = [];
    for (let i = 0; i < 5; i++) {
      const row = el('div', { className: 'poprow' }, this.pop);
      el('span', { className: 'swatch', style: `background:${SPECIES_HEX[i]}` }, row);
      const name = el('span', { className: 'popname' }, row), canvas = el('canvas', { width: 110, height: 22 }, row), value = el('span', { className: 'popvalue' }, row);
      this.popRows.push({ name, canvas, value });
    }
    this.balance = el('div', { id: 'balance' }, this.pop);
  }

  buildInspect() { this.inspect = el('div', { id: 'inspect', style: 'display:none' }, this.root); }

  buildEvents() {
    this.strip = el('div', { id: 'events' }, this.root);
    this.eventCounts = el('div', { id: 'eventcounts' }, this.strip);
    this.eventList = el('div', { id: 'eventlist' }, this.strip);
  }

  buildMinimap() {
    this.mini = el('canvas', { id: 'minimap', width: 240, height: 160 }, this.root);
    this.mini.onclick = (e) => {
      const r = this.mini.getBoundingClientRect(), u = (e.clientX - r.left) / r.width, v = (e.clientY - r.top) / r.height, rel = this.app.relief;
      if (rel) this.app.rig.set('oblique', new THREE.Vector3(u * rel.width * rel.cell, 0, v * rel.depth * rel.cell));
    };
  }

  buildStart() {
    this.start = el('div', { id: 'start', style: 'display:none' }, this.root);
    const box = el('div', { className: 'startbox' }, this.start);
    el('h2', { textContent: 'New run' }, box);
    const field = (label, value, type = 'text') => { const l = el('label', { textContent: label }, box); return el('input', { value, type }, l); };
    this.fSeed = field('Ecology seed', '1111'); this.fTerrain = field('Terrain seed', '20260629');
    const pl = el('label', { textContent: 'Terrain profile' }, box); this.fProfile = el('select', {}, pl);
    for (const p of ['LEGACY_V1', 'PROCEDURAL_V2']) el('option', { value: p, textContent: p }, this.fProfile);
    const tl = el('label', { textContent: 'Thirst' }, box); this.fThirst = el('select', {}, tl);
    for (const p of ['off', 'on']) el('option', { value: p, textContent: p }, this.fThirst);
    el('p', { textContent: 'To watch a recording, start the server with --replay=<file.savrec>.', className: 'hint' }, box);
    const go = el('button', { textContent: 'Start' }, box);
    go.onclick = async () => {
      await this.app.client.control({ cmd: 'reset', seed: parseFloat(this.fSeed.value), terrainSeed: parseFloat(this.fTerrain.value), terrainProfile: this.fProfile.value, thirst: this.fThirst.value });
      this.start.style.display = 'none';
    };
    el('button', { textContent: 'Cancel' }, box).onclick = () => { this.start.style.display = 'none'; };
  }

  setWorld(world) {
    this.world = world; this.series = [[], [], [], [], []]; this.selected = null; this.inspect.style.display = 'none';
    world.species.forEach((s, i) => { this.popRows[i].name.textContent = s.name; });
    this.targets = world.species.map((s) => s.target);
  }

  togglePause() {
    this.paused = !this.paused; this.pauseButton.textContent = this.paused ? 'Resume' : 'Pause';
    this.app.client.control({ cmd: this.paused ? 'pause' : 'resume' });
  }

  toggleFollow() { this.follow = !this.follow; }

  onFrame(frame) {
    this.lastFrameAt = performance.now(); this.frame = frame;
    const day = Math.floor(frame.step / 24) + 1;
    this.clock.textContent = `day ${day}  ${String(frame.hour).padStart(2, '0')}:00  step ${frame.step}  ${WEATHER[frame.weather] || ''}`;
    // Index animals by cell for picking, minimap and density.
    this.index = new Map();
    for (let i = 0; i < frame.animalCount; i++) {
      const key = frame.row[i] * 1000 + frame.col[i]; let list = this.index.get(key); if (!list) this.index.set(key, list = []); list.push(i);
    }
    for (let s = 0; s < 5; s++) { const a = this.series[s]; a.push(frame.speciesCounts[s]); if (a.length > 500) a.shift(); }
    this.drawPopulation(frame); this.drawMinimap(frame); this.updateEvents(frame);
    if (this.selected !== null) this.updateInspect();
    if (this.overlay === 'density' || this.overlay === 'hotspot') this.applyOverlay();
  }

  drawPopulation(frame) {
    const names = this.world.species;
    for (let s = 0; s < 5; s++) {
      const row = this.popRows[s], ctx = row.canvas.getContext('2d'), series = this.series[s], max = Math.max(1, ...series, this.targets[s]) * 1.1;
      ctx.clearRect(0, 0, 110, 22); ctx.strokeStyle = '#ffffff55'; ctx.beginPath(); const ty = 22 - this.targets[s] / max * 22; ctx.moveTo(0, ty); ctx.lineTo(110, ty); ctx.stroke();
      ctx.strokeStyle = SPECIES_HEX[s]; ctx.lineWidth = 1.5; ctx.beginPath();
      series.forEach((v, i) => { const x = i / Math.max(1, series.length - 1) * 110, y = 22 - v / max * 22; if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y); }); ctx.stroke();
      row.value.textContent = `${frame.speciesCounts[s]} / ${names[s].target}`;
    }
    let lo = Infinity, hi = 0;
    for (let s = 0; s < 5; s++) { const r = frame.speciesCounts[s] / this.targets[s]; lo = Math.min(lo, r); hi = Math.max(hi, r); }
    this.balance.textContent = lo > 0 ? `balance (vs targets): ${(hi / lo).toFixed(1)}  (limit 12)` : 'balance: extinct species';
  }

  updateEvents(frame) {
    const n = this.world.species.map((s) => s.name);
    this.eventCounts.textContent = `this step: ${frame.killPredator.length} kills · ${frame.birthChild.length} births · ${frame.deathId.length} deaths`;
    const rel = this.app.relief, cell = rel ? rel.cell : 5;
    for (let k = 0; k < frame.killPredator.length; k++) {
      const pi = this.find(frame, frame.killPredator[k]); const pred = pi >= 0 ? n[frame.species[pi]] : '?';
      this.pushEvent(`${pred} #${frame.killPredator[k]} killed #${frame.killPrey[k]}`, frame.killRow[k], frame.killCol[k], frame.step);
    }
    for (let k = 0; k < frame.birthChild.length && k < 4; k++) {
      const ci = this.find(frame, frame.birthChild[k]);
      if (ci >= 0) this.pushEvent(`${n[frame.species[ci]]} #${frame.birthChild[k]} born`, frame.row[ci], frame.col[ci], frame.step);
    }
    for (let k = 0; k < frame.deathId.length; k++) {
      if (frame.deathCause[k] === 5) continue;
      this.pushEvent(`${n[frame.deathSpecies[k]]} #${frame.deathId[k]} died (${CAUSES[frame.deathCause[k]]})`, frame.deathRow[k], frame.deathCol[k], frame.step);
    }
    this.eventList.innerHTML = '';
    for (const ev of this.events.slice(-5).reverse()) {
      const line = el('div', { className: 'ev', textContent: `[${ev.step}] ${ev.text}` }, this.eventList);
      line.onclick = () => this.app.rig.set('oblique', new THREE.Vector3((ev.col + 0.5) * cell, 0, (ev.row + 0.5) * cell));
    }
  }

  pushEvent(text, row, col, step) { this.events.push({ text, row, col, step }); if (this.events.length > 40) this.events.shift(); }

  find(frame, id) { for (let i = 0; i < frame.animalCount; i++) if (frame.id[i] === id) return i; return -1; }

  // ---- picking and details ----
  pick(clientX, clientY) {
    const app = this.app, rel = app.relief, f = this.frame; if (!rel || !f) return;
    const ndc = new THREE.Vector2(clientX / innerWidth * 2 - 1, -(clientY / innerHeight) * 2 + 1);
    const ray = new THREE.Raycaster(); ray.setFromCamera(ndc, app.camera);
    let t = 1, hit = null;
    for (let i = 0; i < 4000; i++) {                       // march the ray until it dips below the ground
      const p = ray.ray.at(t, new THREE.Vector3());
      if (p.y <= rel.heightAtMeters(p.x, p.z)) { hit = p; break; }
      t += Math.max(1, t * 0.01);
    }
    if (!hit) { this.select(null); return; }
    const row = hit.z / rel.cell, col = hit.x / rel.cell; let best = -1, bestD = 1.5;
    const r0 = Math.floor(row), c0 = Math.floor(col);
    for (let dr = -2; dr <= 2; dr++) for (let dc = -2; dc <= 2; dc++) {
      const list = this.index.get((r0 + dr) * 1000 + c0 + dc); if (!list) continue;
      for (const i of list) { const d = Math.hypot(f.row[i] + 0.5 - row, f.col[i] + 0.5 - col); if (d < bestD) { bestD = d; best = i; } }
    }
    this.select(best >= 0 ? f.id[best] : null);
  }

  select(id) {
    this.selected = id;
    if (id === null) { this.inspect.style.display = 'none'; if (this.app.ring) this.app.ring.visible = false; return false; }
    this.updateInspect(); return this.selected !== null;
  }

  detailsOf(frame, i) {
    const w = this.world, bar = (v) => `${v}%`;
    return {
      id: frame.id[i], species: w.species[frame.species[i]].name, sex: frame.animalFlags[i] & 1 ? 'male' : 'female', stage: STAGES[(frame.animalFlags[i] >> 1) & 3],
      age: `${(frame.age1000[i] / 10).toFixed(1)}% of lifespan`, food: bar(frame.food[i]), stamina: bar(frame.stamina[i]), water: bar(frame.water[i]),
      action: ACTIONS[frame.action[i]] || '?', reason: REASONS[frame.reason[i]] || '?', target: frame.target[i] || '-',
      infected: !!(frame.animalFlags[i] & 8), thirsty: !!(frame.animalFlags[i] & 16), critical: !!(frame.animalFlags[i] & 32), pregnant: !!(frame.animalFlags[i] & 64), resting: !!(frame.animalFlags[i] & 128),
      cell: `${frame.row[i]}, ${frame.col[i]}`,
    };
  }

  updateInspect() {
    const f = this.frame; if (!f) return;
    const i = this.find(f, this.selected);
    if (i < 0) { this.inspect.innerHTML = `<b>#${this.selected}</b> is gone (dead or out of the run)`; this.inspect.style.display = 'block'; this.follow = false; return; }
    const d = this.detailsOf(f, i);
    const flags = ['infected', 'thirsty', 'critical', 'pregnant', 'resting'].filter((k) => d[k]).join(', ') || 'none';
    this.inspect.innerHTML = `<b style="color:${SPECIES_HEX[f.species[i]]}">${d.species} #${d.id}</b> — ${d.sex}, ${d.stage}<br>age ${d.age}<br>food ${d.food} · stamina ${d.stamina} · water ${d.water}<br>action ${d.action} (${d.reason})<br>target ${d.target}<br>flags: ${flags}<br>cell ${d.cell}<br><small>F follow · click empty ground to deselect</small>`;
    this.inspect.style.display = 'block'; this.selectedIndex = i;
  }

  // ---- overlays ----
  applyOverlay() {
    const app = this.app, rel = app.relief, f = this.frame; if (!rel || !app.terrainMesh) return;
    if (this.overlay === 'none') { app.terrainMesh.setOverlay(null); return; }
    const data = new Uint8Array(rel.depth * rel.width);
    if (this.overlay === 'grass' && f && f.grass) data.set(f.grass);
    else if (f && this.overlay === 'density') {
      for (let i = 0; i < f.animalCount; i++) { const k = f.row[i] * rel.width + f.col[i]; data[k] = Math.min(255, data[k] + 120); }
    } else if (f && this.overlay === 'hotspot') {
      const prey = new Uint16Array(rel.depth * rel.width);
      for (let i = 0; i < f.animalCount; i++) if (f.species[i] >= 2) prey[f.row[i] * rel.width + f.col[i]]++;
      const win = 12, sum = (r, c) => { let n = 0; for (let a = 0; a < win; a++) for (let b = 0; b < win; b++) n += prey[Math.min(rel.depth - 1, r + a) * rel.width + Math.min(rel.width - 1, c + b)]; return n; };
      for (let r = 0; r < rel.depth; r += 6) for (let c = 0; c < rel.width; c += 6) { if (sum(r, c) > 30) for (let a = 0; a < 6; a++) for (let b = 0; b < 6; b++) data[Math.min(rel.depth - 1, r + a) * rel.width + Math.min(rel.width - 1, c + b)] = 255; }
    }
    app.terrainMesh.setOverlay(this.overlay, data);
  }

  // ---- minimap ----
  drawMinimap(frame) {
    const rel = this.app.relief; if (!rel) return;
    const ctx = this.mini.getContext('2d'), w = this.mini.width, h = this.mini.height;
    if (!this.miniBase) {
      this.miniBase = document.createElement('canvas'); this.miniBase.width = w; this.miniBase.height = h;
      const b = this.miniBase.getContext('2d'), img = b.createImageData(w, h);
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const t = this.app.terrainTypes[Math.floor(y / h * rel.depth) * rel.width + Math.floor(x / w * rel.width)], c = CONFIG.terrainColors[t], k = (y * w + x) * 4;
        img.data[k] = c[0] * 255; img.data[k + 1] = c[1] * 255; img.data[k + 2] = c[2] * 255; img.data[k + 3] = t === 0 ? 255 : 230;
        if (t === 0) { img.data[k] = 50; img.data[k + 1] = 100; img.data[k + 2] = 160; }
      }
      b.putImageData(img, 0, 0);
    }
    ctx.drawImage(this.miniBase, 0, 0);
    for (let i = 0; i < frame.animalCount; i += 2) {
      ctx.fillStyle = SPECIES_HEX[frame.species[i]]; ctx.fillRect(frame.col[i] / rel.width * w, frame.row[i] / rel.depth * h, 1.5, 1.5);
    }
    const t = this.app.rig.controls.target, cx = t.x / (rel.width * rel.cell) * w, cy = t.z / (rel.depth * rel.cell) * h;
    ctx.strokeStyle = '#fff'; ctx.strokeRect(cx - 5, cy - 4, 10, 8);
  }

  tick(now) {
    const behind = now - this.lastFrameAt > 3500 && !this.paused;
    this.waiting.textContent = behind ? ' waiting for the simulation…' : '';
  }
}
