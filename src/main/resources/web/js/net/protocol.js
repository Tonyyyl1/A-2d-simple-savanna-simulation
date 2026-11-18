// Frame protocol v1 decoder (plan v3.2 appendix B). Pure logic: no DOM, no three.js.
export const PROTOCOL_VERSION = 1;
export const HEADER_BYTES = 80;
const ANIMAL_BYTES = 28;
const MAGIC = 0x31465653; // "SVF1" little endian

export function decodeFrame(buffer) {
  const dv = new DataView(buffer.buffer ? buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength) : buffer);
  if (dv.byteLength < HEADER_BYTES || dv.getUint32(0, true) !== MAGIC) throw new Error('not a simulation frame');
  const version = dv.getUint16(4, true);
  if (version !== PROTOCOL_VERSION || dv.getUint16(6, true) !== HEADER_BYTES) {
    throw new Error('unsupported frame protocol version ' + version);
  }
  const f = {
    generation: dv.getUint32(8, true), sequence: dv.getUint32(12, true), step: dv.getUint32(16, true),
    hour: dv.getUint8(20), phase: dv.getUint8(21), weather: dv.getUint8(22), flags: dv.getUint8(23),
  };
  const animals = dv.getUint32(24, true), paths = dv.getUint32(28, true), kills = dv.getUint32(32, true);
  const births = dv.getUint32(36, true), deaths = dv.getUint32(40, true), grass = dv.getUint32(44, true);
  f.teleportCount = dv.getUint32(48, true);
  f.speciesCounts = [0, 1, 2, 3, 4].map((i) => dv.getUint32(52 + 4 * i, true));
  const expected = HEADER_BYTES + animals * ANIMAL_BYTES + paths * 4 + kills * 12 + births * 8 + deaths * 12 + grass;
  if (expected !== dv.byteLength) throw new Error('frame length ' + dv.byteLength + ' does not match its header (' + expected + ')');
  const col = (n, Type) => new Type(n);
  f.id = col(animals, Uint32Array); f.species = col(animals, Uint8Array); f.animalFlags = col(animals, Uint8Array);
  f.col = col(animals, Uint16Array); f.row = col(animals, Uint16Array); f.prevCol = col(animals, Uint16Array);
  f.prevRow = col(animals, Uint16Array); f.action = col(animals, Uint8Array); f.reason = col(animals, Uint8Array);
  f.effects = col(animals, Uint8Array); f.moveResult = col(animals, Uint8Array); f.age1000 = col(animals, Uint16Array);
  f.food = col(animals, Uint8Array); f.stamina = col(animals, Uint8Array); f.water = col(animals, Uint8Array);
  f.pathLength = col(animals, Uint8Array); f.target = col(animals, Uint32Array);
  let o = HEADER_BYTES;
  for (let i = 0; i < animals; i++, o += ANIMAL_BYTES) {
    f.id[i] = dv.getUint32(o, true); f.species[i] = dv.getUint8(o + 4); f.animalFlags[i] = dv.getUint8(o + 5);
    f.col[i] = dv.getUint16(o + 6, true); f.row[i] = dv.getUint16(o + 8, true);
    f.prevCol[i] = dv.getUint16(o + 10, true); f.prevRow[i] = dv.getUint16(o + 12, true);
    f.action[i] = dv.getUint8(o + 14); f.reason[i] = dv.getUint8(o + 15); f.effects[i] = dv.getUint8(o + 16);
    f.moveResult[i] = dv.getUint8(o + 17); f.age1000[i] = dv.getUint16(o + 18, true);
    f.food[i] = dv.getUint8(o + 20); f.stamina[i] = dv.getUint8(o + 21); f.water[i] = dv.getUint8(o + 22);
    f.pathLength[i] = dv.getUint8(o + 23); f.target[i] = dv.getUint32(o + 24, true);
  }
  f.pathCol = new Uint16Array(paths); f.pathRow = new Uint16Array(paths);
  for (let i = 0; i < paths; i++, o += 4) { f.pathCol[i] = dv.getUint16(o, true); f.pathRow[i] = dv.getUint16(o + 2, true); }
  f.killPredator = new Uint32Array(kills); f.killPrey = new Uint32Array(kills);
  f.killCol = new Uint16Array(kills); f.killRow = new Uint16Array(kills);
  for (let i = 0; i < kills; i++, o += 12) {
    f.killPredator[i] = dv.getUint32(o, true); f.killPrey[i] = dv.getUint32(o + 4, true);
    f.killCol[i] = dv.getUint16(o + 8, true); f.killRow[i] = dv.getUint16(o + 10, true);
  }
  f.birthMother = new Uint32Array(births); f.birthChild = new Uint32Array(births);
  for (let i = 0; i < births; i++, o += 8) { f.birthMother[i] = dv.getUint32(o, true); f.birthChild[i] = dv.getUint32(o + 4, true); }
  f.deathId = new Uint32Array(deaths); f.deathCol = new Uint16Array(deaths); f.deathRow = new Uint16Array(deaths);
  f.deathSpecies = new Uint8Array(deaths); f.deathCause = new Uint8Array(deaths); f.deathFlags = new Uint8Array(deaths);
  for (let i = 0; i < deaths; i++, o += 12) {
    f.deathId[i] = dv.getUint32(o, true); f.deathCol[i] = dv.getUint16(o + 4, true); f.deathRow[i] = dv.getUint16(o + 6, true);
    f.deathSpecies[i] = dv.getUint8(o + 8); f.deathCause[i] = dv.getUint8(o + 9); f.deathFlags[i] = dv.getUint8(o + 10);
  }
  f.grass = grass > 0 ? new Uint8Array(dv.buffer.slice(o, o + grass)) : null;
  f.animalCount = animals;
  return f;
}

/** Splits an /api/frames body of [u32 length][frame] records into frame buffers. */
export function splitFrames(buffer) {
  const dv = new DataView(buffer);
  const frames = [];
  let o = 0;
  while (o + 4 <= dv.byteLength) {
    const n = dv.getUint32(o, true); o += 4;
    frames.push(buffer.slice(o, o + n)); o += n;
  }
  return frames;
}

/** Plain-array view of a decoded frame, for comparison with the JSON written by the Java tools. */
export function toPlain(frame) {
  const out = {};
  for (const [k, v] of Object.entries(frame)) {
    if (k === 'animalCount') continue;
    out[k] = v === null ? null : (ArrayBuffer.isView(v) ? Array.from(v) : v);
  }
  return out;
}
