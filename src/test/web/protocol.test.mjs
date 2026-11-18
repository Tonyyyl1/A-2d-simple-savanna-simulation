import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { decodeFrame, splitFrames, toPlain } from '../../main/resources/web/js/net/protocol.js';

const dir = process.env.WEB_TEST_DIR || 'build/web-test';

test('golden-v1.bin decodes to exactly the JSON the Java side wrote', () => {
  const bin = readFileSync(`${dir}/golden-v1.bin`);
  const expected = JSON.parse(readFileSync(`${dir}/golden-v1.json`, 'utf8'));
  assert.deepStrictEqual(toPlain(decodeFrame(bin)), expected);
});

test('an unknown protocol version is rejected', () => {
  const bin = Buffer.from(readFileSync(`${dir}/golden-v1.bin`));
  bin.writeUInt16LE(2, 4);
  assert.throws(() => decodeFrame(bin), /unsupported frame protocol version 2/);
});

test('a truncated frame is rejected', () => {
  const bin = readFileSync(`${dir}/golden-v1.bin`);
  assert.throws(() => decodeFrame(bin.subarray(0, bin.length - 3)), /does not match its header/);
});

test('split frames reads length-prefixed records', () => {
  const bin = readFileSync(`${dir}/golden-v1.bin`);
  const body = Buffer.alloc(8 + bin.length * 2);
  body.writeUInt32LE(bin.length, 0); bin.copy(body, 4);
  body.writeUInt32LE(bin.length, 4 + bin.length); bin.copy(body, 8 + bin.length);
  const arrayBuffer = body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
  const frames = splitFrames(arrayBuffer);
  assert.equal(frames.length, 2);
  assert.equal(decodeFrame(frames[1]).step, 55);
});

test('world.json lists five species and the explicit code tables', () => {
  const world = JSON.parse(readFileSync(`${dir}/world.json`, 'utf8'));
  assert.equal(world.protocol, 1);
  assert.deepEqual(world.species.map((s) => s.name), ['Lion', 'Cheetah', 'Zebra', 'Buffalo', 'Gazelle']);
  assert.equal(world.codes.action.HUNT, 3);
  assert.equal(world.codes.deathCause.PREDATION, 5);
  assert.equal(world.terrain.depth, 240);
  const terrain = readFileSync(`${dir}/terrain.bin`);
  assert.equal(terrain.length, world.terrain.depth * world.terrain.width);
});

test('height.bin and waterbody.bin match the world dimensions', () => {
  const world = JSON.parse(readFileSync(`${dir}/world.json`, 'utf8'));
  const { depth, width } = world.terrain;
  const heights = readFileSync(`${dir}/height.bin`);
  assert.equal(heights.length, (2 * depth + 1) * (2 * width + 1) * 4);
  const view = new DataView(heights.buffer, heights.byteOffset, heights.byteLength);
  for (let i = 0; i < heights.length; i += 4 * 997) {
    const h = view.getFloat32(i, true);
    assert.ok(Number.isFinite(h) && h > -10 && h < 40, 'height ' + h);
  }
  const bodies = readFileSync(`${dir}/waterbody.bin`);
  assert.equal(bodies.length, depth * width);
  const ids = new Set(bodies);
  assert.ok(ids.has(0) && ids.has(1) && ids.has(2));
  assert.equal(world.terrain.waterLevels.length, 2);
  const terrain = readFileSync(`${dir}/terrain.bin`);
  for (let i = 0; i < terrain.length; i++) assert.equal(terrain[i] === 0, bodies[i] !== 0);
});
