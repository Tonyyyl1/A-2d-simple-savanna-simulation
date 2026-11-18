// Animals: instanced species models, interpolated between the previous cell and the current cell of the latest frame,
// walking animation in the vertex shader, near/far levels of detail, carcasses for fresh deaths.
import * as THREE from 'three';
import { standPoint, idHash } from '../sim/placement.js';
import { speciesGeometry, simpleGeometry } from './proceduralModels.js';

const NEAR_METERS = 170;
const CAPACITY = [900, 900, 6000, 4500, 11000];           // per species, both sexes together
const CARCASS_STEPS = 10;
const ACTION_HUNT = 3;
const REASON_THREAT = 4;

function makeMaterial(timeUniform) {
  const material = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.86, metalness: 0 });
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uTime = timeUniform;
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', '#include <common>\nattribute vec3 aLeg; attribute vec3 aHead; attribute vec3 instAnim; uniform float uTime;')
      .replace('#include <begin_vertex>', `#include <begin_vertex>
float gait = instAnim.y;
float pose = instAnim.z;
if (aHead.z > 0.0 && (pose > 0.5 && pose < 2.5)) {          // 1 graze, 2 drink: head down
  float th = pose < 1.5 ? 0.85 : 1.05;
  float dx = position.x - aHead.x, dy = position.y - aHead.y;
  transformed.x = aHead.x + dx * cos(th) + dy * sin(th);
  transformed.y = aHead.y - dx * sin(th) + dy * cos(th);
}
if (pose > 2.5 && pose < 3.5) { transformed.y = position.y * 0.62; }   // 3 lying down
if (gait > 0.001) {
  float wave = uTime * (4.0 + 5.0 * gait) + instAnim.x;
  if (aLeg.z > 0.0) {
    float w = clamp((aLeg.x - position.y) / aLeg.z, 0.0, 1.0);
    float a = sin(wave + aLeg.y) * 0.55 * min(gait, 1.4) * w;
    float reach = aLeg.x - position.y;
    transformed.x = position.x + reach * sin(a);
    transformed.y = aLeg.x - reach * cos(a);
  }
  transformed.y += abs(sin(wave)) * 0.05 * min(gait, 1.4);
}`);
  };
  return material;
}

export class AnimalRenderer {
  constructor(scene, relief, types) {
    this.relief = relief; this.types = types; this.group = new THREE.Group(); this.group.name = 'animals';
    this.time = { value: 0 };
    this.material = makeMaterial(this.time);
    this.near = []; this.far = []; this.dead = [];
    for (let sp = 0; sp < 5; sp++) {
      const variants = [false, true].map((male) => this.makeMesh(speciesGeometry(sp, male), CAPACITY[sp], true));
      this.near.push(variants);
      this.far.push(this.makeMesh(simpleGeometry(sp), CAPACITY[sp], false));
      this.dead.push(this.makeMesh(speciesGeometry(sp, false), 400, true));
    }
    scene.add(this.group);
    this.frame = null; this.arrival = 0; this.waterViolations = 0; this.maxJump = 0; this.lastPositions = new Map();
    this.carcasses = []; this.lodCounts = [0, 0, 0];
    this.isDry = (row, col) => {
      const level = relief.waterLevelNear(row, col, 4);
      return level === null || relief.heightAtCells(row, col) >= level + 0.05;
    };
  }

  makeMesh(geometry, capacity, animated) {
    const g = geometry.clone();
    if (animated) g.setAttribute('instAnim', new THREE.InstancedBufferAttribute(new Float32Array(capacity * 3), 3).setUsage(THREE.DynamicDrawUsage));
    else { g.setAttribute('aLeg', new THREE.BufferAttribute(new Float32Array(g.getAttribute('position').count * 3), 3)); g.setAttribute('aHead', new THREE.BufferAttribute(new Float32Array(g.getAttribute('position').count * 3), 3)); }
    if (!g.getAttribute('instAnim')) g.setAttribute('instAnim', new THREE.InstancedBufferAttribute(new Float32Array(capacity * 3), 3));
    const mesh = new THREE.InstancedMesh(g, this.material, capacity);
    mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage); mesh.count = 0; mesh.frustumCulled = false; mesh.castShadow = animated;
    this.group.add(mesh); return mesh;
  }

  setFrame(frame, nowMs) {
    // Fresh deaths become carcasses at their cell.
    for (let k = 0; k < frame.deathId.length; k++) {
      const [row, col] = standPoint(frame.deathId[k], frame.deathRow[k], frame.deathCol[k], this.isDry);
      this.carcasses.push({ sp: frame.deathSpecies[k], row, col, step: frame.step, heading: (idHash(frame.deathId[k]) % 628) / 100 });
    }
    this.carcasses = this.carcasses.filter((c) => frame.step - c.step < CARCASS_STEPS);
    this.frame = frame; this.arrival = nowMs;
  }

  /** Latest rendered (x, z) in metres of an animal, or undefined. */
  positionOf(id) { return this.lastPositions.get(id); }

  update(nowMs, stepMs, cameraPosition) {
    const f = this.frame; if (!f) return;
    this.time.value = nowMs / 1000;
    const alpha = Math.max(0, Math.min(1, (nowMs - this.arrival) / stepMs)), cell = this.relief.cell;
    const nearCount = [[0, 0], [0, 0], [0, 0], [0, 0], [0, 0]], farCount = [0, 0, 0, 0, 0];
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), p = new THREE.Vector3(), s = new THREE.Vector3(), up = new THREE.Vector3(0, 1, 0);
    let pathOffset = 0, violations = 0, jump = 0, near = 0, far = 0;
    const near2 = NEAR_METERS * NEAR_METERS;
    for (let i = 0; i < f.animalCount; i++) {
      const id = f.id[i], sp = f.species[i], plen = f.pathLength[i], flags = f.animalFlags[i];
      const pts = [standPoint(id, f.prevRow[i], f.prevCol[i], this.isDry)];
      for (let k = 0; k < plen; k++) pts.push(standPoint(id, f.pathRow[pathOffset + k], f.pathCol[pathOffset + k], this.isDry));
      pts.push(standPoint(id, f.row[i], f.col[i], this.isDry));
      pathOffset += plen;
      const seg = (pts.length - 1) * alpha, a = Math.min(pts.length - 2, Math.floor(seg)), t = seg - a;
      const p0 = pts[a], p1 = pts[a + 1];
      const row = p0[0] + (p1[0] - p0[0]) * t, col = p0[1] + (p1[1] - p0[1]) * t, dRow = p1[0] - p0[0], dCol = p1[1] - p0[1];
      const x = col * cell, z = row * cell, y = this.relief.heightAtMeters(x, z);
      const level = this.relief.waterLevelNear(row, col, 4);
      if (level !== null && y < level + 0.02) violations++;
      const previous = this.lastPositions.get(id);
      if (previous) jump = Math.max(jump, Math.hypot(previous[0] - x, previous[1] - z));
      this.lastPositions.set(id, [x, z]);
      const moving = (dRow !== 0 || dCol !== 0) && alpha < 1;
      const heading = (dRow !== 0 || dCol !== 0) ? Math.atan2(dRow, dCol) : (id % 628) / 100;
      const running = f.action[i] === ACTION_HUNT || f.reason[i] === REASON_THREAT;
      const gait = moving ? (running ? 1.5 : 0.8) : 0;
      const effects = f.effects[i], pose = (flags & 128) ? 3 : (!moving && (effects & 1)) ? 2 : (!moving && (effects & 4)) ? 1 : 0;
      const stage = (flags >> 1) & 3, scale = stage === 0 ? 0.45 : stage === 1 ? 0.72 : 1;
      q.setFromAxisAngle(up, -heading); p.set(x, y, z); s.setScalar(scale); m.compose(p, q, s);
      const dx = x - cameraPosition.x, dz = z - cameraPosition.z, dy = y - cameraPosition.y;
      if (dx * dx + dz * dz + dy * dy < near2) {
        const male = flags & 1 ? 1 : 0, mesh = this.near[sp][male], index = nearCount[sp][male]++;
        mesh.setMatrixAt(index, m);
        const anim = mesh.geometry.getAttribute('instAnim'); anim.setXYZ(index, (idHash(id) & 1023) / 160, gait, pose);
        near++;
      }
      else {
        const mesh = this.far[sp], index = farCount[sp]++;
        mesh.setMatrixAt(index, m); far++;
      }
    }
    for (let sp = 0; sp < 5; sp++) {
      for (let v = 0; v < 2; v++) {
        const mesh = this.near[sp][v]; mesh.count = nearCount[sp][v]; mesh.instanceMatrix.needsUpdate = true;
        mesh.geometry.getAttribute('instAnim').needsUpdate = true;
      }
      this.far[sp].count = farCount[sp]; this.far[sp].instanceMatrix.needsUpdate = true;
    }
    // Carcasses: lying on their side, slowly sinking out of view.
    const deadCount = [0, 0, 0, 0, 0], side = new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(1, 0, 0), Math.PI / 2);
    for (const c of this.carcasses) {
      const age = (f.step - c.step + alpha) / CARCASS_STEPS, x = c.col * cell, z = c.row * cell;
      q.setFromAxisAngle(up, c.heading).multiply(side);
      p.set(x, this.relief.heightAtMeters(x, z) + 0.45 - Math.max(0, age - 0.7) * 1.2, z); s.setScalar(1); m.compose(p, q, s);
      this.dead[c.sp].setMatrixAt(deadCount[c.sp]++, m);
    }
    for (let sp = 0; sp < 5; sp++) { this.dead[sp].count = deadCount[sp]; this.dead[sp].instanceMatrix.needsUpdate = true; }
    this.lodCounts = [near, far, this.carcasses.length];
    this.waterViolations = violations; this.maxJump = Math.max(this.maxJump * 0.995, jump);
    if (this.lastPositions.size > 40000) this.lastPositions.clear();
  }
}
