// Low-poly procedural species models (V4 fallback that needs no external assets).
// Every model is one merged geometry with vertex colours plus attribute aLeg = (pivot height, phase offset, leg length)
// for leg vertices (leg length 0 elsewhere); the vertex shader swings those vertices around the pivot.
import * as THREE from 'three';
import { mergeGeometries } from '../../vendor/examples/jsm/utils/BufferGeometryUtils.js';

const NO_LEG = [0, 0, 0];

function part(geometry, color, { pos = [0, 0, 0], scale = [1, 1, 1], rot = [0, 0, 0], leg = NO_LEG, noise = 0, stripes = null, belly = null } = {}) {
  const g = geometry.index ? geometry.toNonIndexed() : geometry.clone();
  g.scale(scale[0], scale[1], scale[2]);
  g.rotateX(rot[0]); g.rotateY(rot[1]); g.rotateZ(rot[2]);
  g.translate(pos[0], pos[1], pos[2]);
  const p = g.getAttribute('position'), n = p.count, colors = new Float32Array(n * 3), legs = new Float32Array(n * 3), c = new THREE.Color(color);
  for (let i = 0; i < n; i++) {
    let r = c.r, gg = c.g, b = c.b;
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    if (noise) { const k = 1 + noise * (Math.sin(x * 23.0 + z * 31.0) * Math.sin(y * 19.0 + x * 7.0)); r *= k; gg *= k; b *= k; }
    if (stripes) { const s = Math.sin(x * stripes) > 0 ? 1 : 0.12; r = c.r * s + 0.02; gg = c.g * s + 0.02; b = c.b * s + 0.02; }
    if (belly) { const t = Math.max(0, Math.min(1, (belly - y) / 0.18)); r = r * (1 - t) + 0.92 * t; gg = gg * (1 - t) + 0.9 * t; b = b * (1 - t) + 0.86 * t; }
    colors[3 * i] = r; colors[3 * i + 1] = gg; colors[3 * i + 2] = b;
    if (leg[2] > 0) { legs[3 * i] = leg[0]; legs[3 * i + 1] = leg[1]; legs[3 * i + 2] = leg[2]; }
  }
  g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  g.setAttribute('aLeg', new THREE.BufferAttribute(legs, 3));
  return g;
}

const BOX = new THREE.BoxGeometry(1, 1, 1);
const SPHERE = new THREE.IcosahedronGeometry(0.5, 1);
const CYL = new THREE.CylinderGeometry(0.5, 0.5, 1, 6);
const CONE = new THREE.ConeGeometry(0.5, 1, 6);

/** Four legs: front pair, rear pair; diagonal gait phases. */
function legs(parts, color, { length, thick, frontX, rearX, halfWidth, top, tint = 1 }) {
  const specs = [[frontX, halfWidth, 0], [frontX, -halfWidth, Math.PI], [rearX, halfWidth, Math.PI], [rearX, -halfWidth, 0]];
  for (const [x, z, phase] of specs) {
    parts.push(part(CYL, color, { pos: [x, top - length / 2, z], scale: [thick, length, thick], leg: [top, phase, length] }));
    parts.push(part(BOX, 0x2a2420, { pos: [x + thick * 0.2, 0.04, z], scale: [thick * 1.4, 0.08, thick * 1.1], leg: [top, phase, length] }));
  }
}

function build(species, male) {
  const parts = [];
  if (species === 0) {                                         // Lion
    const c = 0xc79a58;
    parts.push(part(SPHERE, c, { pos: [0, 1.05, 0], scale: [1.9, 0.78, 0.7], noise: 0.06, belly: 0.82 }));
    legs(parts, c, { length: 0.72, thick: 0.2, frontX: 0.65, rearX: -0.62, halfWidth: 0.24, top: 0.78 });
    parts.push(part(SPHERE, c, { pos: [1.12, 1.35, 0], scale: [0.5, 0.46, 0.44] }));
    parts.push(part(BOX, 0x8a6a45, { pos: [1.4, 1.27, 0], scale: [0.22, 0.2, 0.26] }));
    if (male) parts.push(part(SPHERE, 0x4a3320, { pos: [0.95, 1.32, 0], scale: [0.75, 0.82, 0.82], noise: 0.15 }));
    parts.push(part(CYL, c, { pos: [-1.12, 1.0, 0], scale: [0.09, 0.9, 0.09], rot: [0, 0, 1.0] }));
    parts.push(part(SPHERE, 0x3a2a1a, { pos: [-1.5, 0.62, 0], scale: [0.17, 0.2, 0.17] }));
  } else if (species === 1) {                                  // Cheetah
    const c = 0xdcb35a;
    parts.push(part(SPHERE, c, { pos: [0, 0.82, 0], scale: [1.25, 0.46, 0.36], noise: 0.45, belly: 0.65 }));
    legs(parts, c, { length: 0.62, thick: 0.12, frontX: 0.42, rearX: -0.4, halfWidth: 0.13, top: 0.7 });
    parts.push(part(SPHERE, c, { pos: [0.78, 1.0, 0], scale: [0.3, 0.28, 0.26], noise: 0.4 }));
    parts.push(part(CYL, c, { pos: [0.52, 0.95, 0], scale: [0.1, 0.34, 0.1], rot: [0, 0, -0.7] }));
    parts.push(part(CYL, c, { pos: [-0.9, 0.9, 0], scale: [0.06, 0.95, 0.06], rot: [0, 0, 1.15], noise: 0.4 }));
  } else if (species === 2) {                                  // Zebra
    const c = 0xf0f0f0;
    parts.push(part(SPHERE, c, { pos: [0, 1.2, 0], scale: [1.9, 0.86, 0.66], stripes: 9, belly: 0.9 }));
    legs(parts, c, { length: 0.82, thick: 0.15, frontX: 0.62, rearX: -0.62, halfWidth: 0.2, top: 0.9, tint: 1 });
    parts.push(part(CYL, c, { pos: [0.95, 1.55, 0], scale: [0.26, 0.7, 0.24], rot: [0, 0, -0.75], stripes: 14 }));
    parts.push(part(SPHERE, c, { pos: [1.35, 1.78, 0], scale: [0.5, 0.3, 0.26], stripes: 18 }));
    parts.push(part(BOX, 0x1b1b1b, { pos: [1.05, 1.82, 0], scale: [0.6, 0.12, 0.05], rot: [0, 0, -0.75] }));
    parts.push(part(CYL, 0x1b1b1b, { pos: [-1.05, 1.15, 0], scale: [0.06, 0.6, 0.06], rot: [0, 0, 0.35] }));
  } else if (species === 3) {                                  // Buffalo
    const c = 0x3b352f;
    parts.push(part(SPHERE, c, { pos: [0, 1.1, 0], scale: [2.2, 1.0, 0.9], noise: 0.08 }));
    legs(parts, c, { length: 0.8, thick: 0.22, frontX: 0.7, rearX: -0.72, halfWidth: 0.3, top: 0.9 });
    parts.push(part(SPHERE, c, { pos: [1.3, 1.0, 0], scale: [0.7, 0.62, 0.62] }));
    parts.push(part(BOX, 0x5a5048, { pos: [1.28, 1.5, 0], scale: [0.5, 0.18, 0.9] }));
    for (const s of [-1, 1]) parts.push(part(CONE, 0xd8d0c0, { pos: [1.25, 1.5, s * 0.55], scale: [0.14, 0.7, 0.14], rot: [s * -1.2, 0, 0] }));
    parts.push(part(CYL, c, { pos: [-1.2, 1.0, 0], scale: [0.07, 0.7, 0.07], rot: [0, 0, 0.3] }));
  } else {                                                     // Gazelle
    const c = 0xc89a64;
    parts.push(part(SPHERE, c, { pos: [0, 0.92, 0], scale: [1.0, 0.4, 0.3], belly: 0.82, noise: 0.05 }));
    legs(parts, c, { length: 0.68, thick: 0.07, frontX: 0.32, rearX: -0.32, halfWidth: 0.1, top: 0.8 });
    parts.push(part(CYL, c, { pos: [0.5, 1.15, 0], scale: [0.11, 0.5, 0.1], rot: [0, 0, -0.6] }));
    parts.push(part(SPHERE, c, { pos: [0.74, 1.42, 0], scale: [0.26, 0.2, 0.18] }));
    if (male) for (const s of [-1, 1]) parts.push(part(CONE, 0x2b2420, { pos: [0.7, 1.62, s * 0.07], scale: [0.04, 0.4, 0.04], rot: [0, 0, 0.15] }));
    parts.push(part(BOX, 0x20201c, { pos: [-0.5, 0.95, 0], scale: [0.12, 0.08, 0.04] }));
  }
  const merged = mergeGeometries(parts, false);
  merged.computeVertexNormals();
  return merged;
}

const cache = new Map();
export function speciesGeometry(species, male) {
  const key = species + ':' + (male ? 1 : 0);
  if (!cache.has(key)) cache.set(key, build(species, male));
  return cache.get(key);
}

/** Cheap far-distance stand-in: one tinted box of the right proportions. */
const SIMPLE = [[2.6, 1.4, 0.8, 0xc79a58], [1.5, 0.9, 0.36, 0xdcb35a], [2.3, 1.6, 0.66, 0xdddddd], [2.8, 1.7, 0.9, 0x3b352f], [1.1, 1.1, 0.3, 0xc89a64]];
export function simpleGeometry(species) {
  const [l, h, w, color] = SIMPLE[species];
  return part(BOX, color, { pos: [0, h / 2, 0], scale: [l, h, w] });
}
