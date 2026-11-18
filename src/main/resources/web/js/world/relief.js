// Height sampling on the vertex grid delivered by /api/world/height.bin (pure logic).
export class Relief {
  constructor({ heights, bodies, depth, width, waterLevels, cellMeters }) {
    this.heights = heights; this.bodies = bodies; this.depth = depth; this.width = width;
    this.waterLevels = waterLevels; this.cell = cellMeters;
    this.vDepth = 2 * depth + 1; this.vWidth = 2 * width + 1;
  }
  vertexHeight(vr, vc) { return this.heights[vr * this.vWidth + vc]; }
  /** Bilinear height at world metres (x east, z south). */
  heightAtMeters(x, z) { return this.heightAtCells(z / this.cell, x / this.cell); }
  heightAtCells(row, col) {
    const vr = Math.max(0, Math.min(this.vDepth - 1.001, row * 2));
    const vc = Math.max(0, Math.min(this.vWidth - 1.001, col * 2));
    const r0 = Math.floor(vr), c0 = Math.floor(vc), fr = vr - r0, fc = vc - c0;
    const h = this.heights, w = this.vWidth;
    const top = h[r0 * w + c0] * (1 - fc) + h[r0 * w + c0 + 1] * fc;
    const bottom = h[(r0 + 1) * w + c0] * (1 - fc) + h[(r0 + 1) * w + c0 + 1] * fc;
    return top * (1 - fr) + bottom * fr;
  }
  bodyAt(row, col) {
    if (row < 0 || col < 0 || row >= this.depth || col >= this.width) return 0;
    return this.bodies[row * this.width + col];
  }
  /** Water level of the body whose box (grown by margin cells) holds the point, or null. */
  waterLevelNear(row, col, margin = 4) {
    for (const b of this.boxes()) {
      if (row >= b.r0 - margin && row <= b.r1 + margin && col >= b.c0 - margin && col <= b.c1 + margin) return b.level;
    }
    return null;
  }
  boxes() {
    if (this._boxes) return this._boxes;
    const boxes = [];
    for (let id = 1; id <= this.waterLevels.length; id++) boxes.push({ id, r0: 1e9, c0: 1e9, r1: -1, c1: -1, level: this.waterLevels[id - 1] });
    for (let r = 0; r < this.depth; r++) for (let c = 0; c < this.width; c++) {
      const id = this.bodies[r * this.width + c];
      if (!id) continue;
      const b = boxes[id - 1];
      b.r0 = Math.min(b.r0, r); b.c0 = Math.min(b.c0, c); b.r1 = Math.max(b.r1, r); b.c1 = Math.max(b.c1, c);
    }
    return (this._boxes = boxes);
  }
}
