// Optional (set FIXTURE=<file.savrec>): replays a recorded run through the client placement logic and checks
// that no stand point or path sample is ever wet and that no animal teleports.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { inflateSync } from 'node:zlib';
import { Relief } from '../../main/resources/web/js/world/relief.js';
import { standPoint } from '../../main/resources/web/js/sim/placement.js';
import { decodeFrame } from '../../main/resources/web/js/net/protocol.js';

const file = process.env.FIXTURE;
test('recorded run stays dry and continuous', { skip: !file || !existsSync(file) }, () => {
  const buf = readFileSync(file);
  assert.equal(buf.subarray(0, 8).toString('latin1'), 'SAVREC1\n');
  let o = 8;
  const block = () => { const n = buf.readUInt32LE(o); o += 4; const b = buf.subarray(o, o + n); o += n; return b; };
  const world = JSON.parse(block().toString('utf8')), terrain = block(), heights = block(), bodies = block();
  const h = new Float32Array(heights.buffer.slice(heights.byteOffset, heights.byteOffset + heights.byteLength));
  const relief = new Relief({ heights: h, bodies: new Uint8Array(bodies), depth: world.terrain.depth, width: world.terrain.width, waterLevels: world.terrain.waterLevels, cellMeters: 5 });
  const isDry = (row, col) => { const level = relief.waterLevelNear(row, col, 4); return level === null || relief.heightAtCells(row, col) >= level + 0.05; };
  let frames = 0, samples = 0, maxCells = 0, speciesMax = [14, 14, 14, 14, 14];
  while (o < buf.length) {
    const n = buf.readUInt32LE(o); o += 4;
    const frame = decodeFrame(inflateSync(buf.subarray(o, o + n))); o += n; frames++;
    assert.equal(frame.teleportCount, 0, 'teleport in frame ' + frame.sequence);
    let po = 0;
    for (let i = 0; i < frame.animalCount; i++) {
      const id = frame.id[i], plen = frame.pathLength[i];
      const pts = [standPoint(id, frame.prevRow[i], frame.prevCol[i], isDry)];
      for (let k = 0; k < plen; k++) pts.push(standPoint(id, frame.pathRow[po + k], frame.pathCol[po + k], isDry));
      pts.push(standPoint(id, frame.row[i], frame.col[i], isDry)); po += plen;
      for (let s = 0; s + 1 < pts.length; s++) for (let k = 0; k <= 8; k++) {
        const t = k / 8; assert.ok(isDry(pts[s][0] + (pts[s + 1][0] - pts[s][0]) * t, pts[s][1] + (pts[s + 1][1] - pts[s][1]) * t), `wet sample frame ${frame.sequence} animal ${id}`); samples++;
      }
      maxCells = Math.max(maxCells, Math.max(Math.abs(frame.row[i] - frame.prevRow[i]), Math.abs(frame.col[i] - frame.prevCol[i])));
    }
  }
  assert.ok(frames > 1);
  console.log(`fixture: ${frames} frames, ${samples} path samples dry, longest move ${maxCells} cells`);
});
