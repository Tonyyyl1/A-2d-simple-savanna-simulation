// Stand positions: cell centre plus a hash-based offset (<= 0.3 cell), pulled back until it is on dry land.
export function idHash(id) {
  let h = id | 0;
  h = Math.imul(h ^ (h >>> 16), 0x45d9f3b); h = Math.imul(h ^ (h >>> 16), 0x45d9f3b); h ^= h >>> 16;
  return h >>> 0;
}

/** Offset in cell units for an animal id; deterministic and independent of the simulation. */
export function standOffset(id) {
  const h = idHash(id), angle = (h & 0xffff) / 65536 * Math.PI * 2, radius = 0.3 * Math.sqrt(((h >>> 16) & 0xffff) / 65536);
  return [Math.sin(angle) * radius, Math.cos(angle) * radius];
}

/**
 * Standing point in cell units, (row, col): centre + offset; when the ground there is below water level + margin
 * the offset is halved (at most 4 times) and finally dropped. `isDry(row, col)` decides.
 */
export function standPoint(id, row, col, isDry) {
  let [dr, dc] = standOffset(id);
  for (let i = 0; i < 4; i++) {
    if (isDry(row + 0.5 + dr, col + 0.5 + dc)) return [row + 0.5 + dr, col + 0.5 + dc];
    dr *= 0.5; dc *= 0.5;
  }
  return [row + 0.5, col + 0.5];
}
