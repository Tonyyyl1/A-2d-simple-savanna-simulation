// Chunked terrain mesh with smooth, noise-warped terrain-type colouring and grass-fill tinting.
import * as THREE from 'three';
import { CONFIG } from '../config.js';

function hash2(x, y) {
  let h = (x * 374761393 + y * 668265263) | 0;
  h = (h ^ (h >>> 13)) * 1274126177 | 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}
function valueNoise(x, y) {
  const x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0;
  const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
  const a = hash2(x0, y0), b = hash2(x0 + 1, y0), c = hash2(x0, y0 + 1), d = hash2(x0 + 1, y0 + 1);
  return (a * (1 - sx) + b * sx) * (1 - sy) + (c * (1 - sx) + d * sx) * sy;
}

export class TerrainMesh {
  constructor(relief, terrainTypes) {
    this.relief = relief; this.types = terrainTypes;
    this.group = new THREE.Group(); this.group.name = 'terrain';
    this.chunks = [];
    this.fill = null; this.overlay = null; this.overlayData = null;
    const n = CONFIG.chunkCells;
    const material = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.96, metalness: 0 });
    for (let r0 = 0; r0 < relief.depth; r0 += n) {
      for (let c0 = 0; c0 < relief.width; c0 += n) {
        const chunk = this.buildChunk(r0, c0, Math.min(n, relief.depth - r0), Math.min(n, relief.width - c0), material);
        this.chunks.push(chunk); this.group.add(chunk.mesh);
      }
    }
  }

  typeColor(row, col) {
    const r = Math.max(0, Math.min(this.relief.depth - 1, row)), c = Math.max(0, Math.min(this.relief.width - 1, col));
    return CONFIG.terrainColors[this.types[r * this.relief.width + c]];
  }

  /** Smooth colour at a position in cell units: warped bilinear blend of the four nearest cell colours. */
  colorAt(row, col, out) {
    const wr = row + (valueNoise(col * 0.35, row * 0.35) - 0.5) * 1.6;
    const wc = col + (valueNoise(col * 0.35 + 91, row * 0.35 + 17) - 0.5) * 1.6;
    const u = wr - 0.5, v = wc - 0.5, r0 = Math.floor(u), c0 = Math.floor(v), fr = u - r0, fc = v - c0;
    const sr = fr * fr * (3 - 2 * fr), sc = fc * fc * (3 - 2 * fc);
    const a = this.typeColor(r0, c0), b = this.typeColor(r0, c0 + 1), c = this.typeColor(r0 + 1, c0), d = this.typeColor(r0 + 1, c0 + 1);
    for (let k = 0; k < 3; k++) out[k] = (a[k] * (1 - sc) + b[k] * sc) * (1 - sr) + (c[k] * (1 - sc) + d[k] * sc) * sr;
    const grain = 0.88 + 0.24 * valueNoise(col * 1.7, row * 1.7) * 0.6 + 0.12 * valueNoise(col * 5.3, row * 5.3);
    out[0] *= grain; out[1] *= grain; out[2] *= grain;
    return out;
  }

  buildChunk(r0, c0, rows, cols, material) {
    const relief = this.relief, cell = relief.cell;
    const vr0 = r0 * 2, vc0 = c0 * 2, nr = rows * 2 + 1, nc = cols * 2 + 1;
    const positions = new Float32Array(nr * nc * 3), colors = new Float32Array(nr * nc * 3);
    const index = new Uint32Array(rows * 2 * cols * 2 * 6);
    let p = 0;
    for (let i = 0; i < nr; i++) for (let j = 0; j < nc; j++) {
      const vr = vr0 + i, vc = vc0 + j, k = (i * nc + j) * 3;
      positions[k] = vc / 2 * cell; positions[k + 1] = relief.vertexHeight(vr, vc); positions[k + 2] = vr / 2 * cell;
    }
    for (let i = 0; i < nr - 1; i++) for (let j = 0; j < nc - 1; j++) {
      const a = i * nc + j, b = a + 1, c = a + nc, d = c + 1;
      index[p++] = a; index[p++] = c; index[p++] = b; index[p++] = b; index[p++] = c; index[p++] = d;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    geometry.setIndex(new THREE.BufferAttribute(index, 1));
    geometry.computeVertexNormals();
    geometry.computeBoundingSphere(); geometry.computeBoundingBox();
    const mesh = new THREE.Mesh(geometry, material);
    mesh.receiveShadow = true; mesh.castShadow = false;
    const chunk = { mesh, r0, c0, rows, cols, nr, nc, vr0, vc0 };
    this.paintChunk(chunk);
    return chunk;
  }

  paintChunk(chunk) {
    const colors = chunk.mesh.geometry.getAttribute('color'), tmp = [0, 0, 0], dry = CONFIG.grassDry, relief = this.relief;
    if (!chunk.base) {           // the noisy base colours never change: compute them once
      chunk.base = new Float32Array(chunk.nr * chunk.nc * 3);
      for (let i = 0; i < chunk.nr; i++) for (let j = 0; j < chunk.nc; j++) {
        this.colorAt((chunk.vr0 + i) / 2, (chunk.vc0 + j) / 2, tmp);
        chunk.base.set(tmp, (i * chunk.nc + j) * 3);
      }
    }
    for (let i = 0; i < chunk.nr; i++) for (let j = 0; j < chunk.nc; j++) {
      const vr = chunk.vr0 + i, vc = chunk.vc0 + j, row = vr / 2, col = vc / 2;
      const b = (i * chunk.nc + j) * 3;
      tmp[0] = chunk.base[b]; tmp[1] = chunk.base[b + 1]; tmp[2] = chunk.base[b + 2];
      // Vegetated types shift between dry and lush with the grass fill.
      const cr = Math.max(0, Math.min(relief.depth - 1, Math.floor(row))), cc = Math.max(0, Math.min(relief.width - 1, Math.floor(col)));
      const type = this.types[cr * relief.width + cc];
      if (this.fill && (type === 1 || type === 2 || type === 3)) {
        const f = this.fill[cr * relief.width + cc] / 255, lush = type === 3 ? 0.4 : 1;
        const t = Math.min(1, f * lush + (type === 3 ? 0.35 : 0));
        for (let k = 0; k < 3; k++) tmp[k] = dry[k] * (1 - t) + tmp[k] * t;
      }
      // Wet mud near the water line.
      const level = relief.waterLevelNear(row, col, 3);
      if (level !== null) {
        const h = relief.vertexHeight(vr, vc), wet = Math.max(0, 1 - (h - level) / 0.5);
        if (wet > 0) for (let k = 0; k < 3; k++) tmp[k] = tmp[k] * (1 - 0.55 * wet) + [0.24, 0.20, 0.15][k] * 0.55 * wet;
      }
      if (this.overlayData) {
        const v = this.overlayData[cr * relief.width + cc] / 255, heat = this.overlay === 'grass' ? [0.9 * (1 - v), 0.25 + 0.7 * v, 0.1] : [0.2 + 0.8 * v, 0.9 - 0.7 * v, 0.2];
        const a = this.overlay === 'hotspot' ? (v > 0 ? 0.65 : 0) : 0.7;
        for (let q = 0; q < 3; q++) tmp[q] = tmp[q] * (1 - a) + heat[q] * a;
      }
      const k = (i * chunk.nc + j) * 3;
      colors.array[k] = tmp[0]; colors.array[k + 1] = tmp[1]; colors.array[k + 2] = tmp[2];
    }
    colors.needsUpdate = true;
  }

  setOverlay(kind, data) {
    this.overlay = kind; this.overlayData = kind ? data : null;
    for (const chunk of this.chunks) this.paintChunk(chunk);
  }

  /** New grass-fill bytes (depth x width); repaints the chunks. */
  setGrass(fill) {
    this.fill = fill;
    for (const chunk of this.chunks) this.paintChunk(chunk);
  }
}
