import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Relief } from '../../main/resources/web/js/world/relief.js';
import { standPoint, standOffset } from '../../main/resources/web/js/sim/placement.js';

const dir = process.env.WEB_TEST_DIR || 'build/web-test';
const world = JSON.parse(readFileSync(`${dir}/world.json`, 'utf8'));
const f32 = (file) => { const b = readFileSync(file); return new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength)); };
const relief = new Relief({
  heights: f32(`${dir}/height.bin`), bodies: new Uint8Array(readFileSync(`${dir}/waterbody.bin`)),
  depth: world.terrain.depth, width: world.terrain.width, waterLevels: world.terrain.waterLevels, cellMeters: 5,
});
const isDry = (row, col) => { const level = relief.waterLevelNear(row, col, 4); return level === null || relief.heightAtCells(row, col) >= level + 0.05; };

test('offsets stay within 0.3 cell and are deterministic', () => {
  for (let id = 1; id < 5000; id++) {
    const [a, b] = standOffset(id), [c, d] = standOffset(id);
    assert.ok(Math.hypot(a, b) <= 0.3 + 1e-9);
    assert.deepEqual([a, b], [c, d]);
  }
});

test('every land cell gives a dry stand point for eight ids', () => {
  for (let r = 0; r < relief.depth; r++) for (let c = 0; c < relief.width; c++) {
    if (relief.bodyAt(r, c) !== 0) continue;
    for (let k = 1; k <= 8; k++) {
      const [row, col] = standPoint(r * 977 + c * 31 + k, r, c, isDry);
      assert.ok(isDry(row, col), `wet stand point at cell ${r},${c} id ${k}`);
      assert.ok(Math.abs(row - (r + 0.5)) <= 0.3 + 1e-9 && Math.abs(col - (c + 0.5)) <= 0.3 + 1e-9);
    }
  }
});

test('every 8-neighbour land step is dry along the way for any offset', () => {
  let checked = 0;
  for (let r = 1; r < relief.depth - 1; r += 3) for (let c = 1; c < relief.width - 1; c += 3) {
    if (relief.bodyAt(r, c) !== 0) continue;
    for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) {
      if ((dr === 0 && dc === 0) || relief.bodyAt(r + dr, c + dc) !== 0) continue;
      const id = r * 13 + c * 7 + dr * 3 + dc;
      const [r0, c0] = standPoint(id, r, c, isDry), [r1, c1] = standPoint(id, r + dr, c + dc, isDry);
      for (let k = 0; k <= 8; k++) { const t = k / 8; assert.ok(isDry(r0 + (r1 - r0) * t, c0 + (c1 - c0) * t), `wet path ${r},${c} -> ${r + dr},${c + dc}`); checked++; }
    }
  }
  assert.ok(checked > 1000);
});
