// Trees, bushes (fixed by the terrain seed, at cell corners) and camera-local grass tufts.
import * as THREE from 'three';
import { CONFIG } from '../config.js';

function hash3(a, b, c) {
  let h = (a * 374761393 + b * 668265263 + c * 2147483647) | 0;
  h = (h ^ (h >>> 13)) * 1274126177 | 0;
  return ((h ^ (h >>> 16)) >>> 0) / 4294967295;
}

export class Vegetation {
  constructor(relief, types, seed, qualityName) {
    this.relief = relief; this.types = types; this.group = new THREE.Group(); this.group.name = 'vegetation';
    const q = CONFIG.quality[qualityName] || CONFIG.quality.medium;
    this.maxGrass = q.grassInstances;
    this.buildTrees(seed, q.treeDensity);
    this.buildGrass();
    this.lastCamera = new THREE.Vector3(1e9, 0, 1e9); this.lastFill = null;
  }

  buildTrees(seed, density) {
    const { depth, width, cell } = this.relief, trunk = [], crowns = [], bushes = [];
    const chance = [0, 0.012, 0.16, 0.02, 0.004];             // per cell corner, by terrain code
    for (let vr = 1; vr < depth; vr++) for (let vc = 1; vc < width; vc++) {
      // The corner (vr, vc) touches cells (vr-1..vr, vc-1..vc); skip it when any is water.
      const touching = [this.types[(vr - 1) * width + vc - 1], this.types[(vr - 1) * width + vc], this.types[vr * width + vc - 1], this.types[vr * width + vc]];
      if (touching.includes(0)) continue;
      const t = touching[(hash3(vr, vc, seed) * 4) | 0];
      if (hash3(vr, vc, seed + 1) > chance[t] * density) continue;
      const x = vc * cell, z = vr * cell, y = this.relief.heightAtCells(vr, vc);
      const scale = 0.7 + hash3(vr, vc, seed + 2) * 0.8;
      if (t === 2) bushes.push([x, y, z, scale]); else { trunk.push([x, y, z, scale]); crowns.push([x, y, z, scale]); }
    }
    const make = (geometry, color, items, fn, roughness = 0.9) => {
      const mesh = new THREE.InstancedMesh(geometry, new THREE.MeshStandardMaterial({ color, roughness, flatShading: true }), Math.max(1, items.length));
      const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3();
      items.forEach((it, i) => { fn(it, p, q, s); m.compose(p, q, s); mesh.setMatrixAt(i, m); });
      mesh.count = items.length; mesh.castShadow = true; mesh.receiveShadow = true; mesh.frustumCulled = true;
      return mesh;
    };
    const trunkGeometry = new THREE.CylinderGeometry(0.18, 0.3, 4.2, 6); trunkGeometry.translate(0, 2.1, 0);
    const crownGeometry = new THREE.SphereGeometry(3.4, 8, 5); crownGeometry.scale(1, 0.34, 1); crownGeometry.translate(0, 4.6, 0);
    const bushGeometry = new THREE.IcosahedronGeometry(1.1, 0); bushGeometry.scale(1.3, 0.8, 1.3); bushGeometry.translate(0, 0.7, 0);
    this.group.add(make(trunkGeometry, 0x5a4330, trunk, (it, p, q, s) => { p.set(it[0], it[1], it[2]); q.identity(); s.setScalar(it[3]); }));
    this.group.add(make(crownGeometry, 0x7c8f3a, crowns, (it, p, q, s) => { p.set(it[0], it[1], it[2]); q.identity(); s.setScalar(it[3]); }));
    this.group.add(make(bushGeometry, 0x3f6b2d, bushes, (it, p, q, s) => { p.set(it[0], it[1], it[2]); q.identity(); s.setScalar(it[3]); }));
    this.treeCount = trunk.length; this.bushCount = bushes.length;
  }

  buildGrass() {
    const blade = new THREE.BufferGeometry();
    const v = new Float32Array([-0.045, 0, 0, 0.045, 0, 0, 0.01, 0.34, 0, 0, 0, -0.045, 0, 0, 0.045, -0.01, 0.3, 0.01]);
    blade.setAttribute('position', new THREE.BufferAttribute(v, 3));
    blade.computeVertexNormals();
    const material = new THREE.MeshStandardMaterial({ color: 0x9bb552, roughness: 1, side: THREE.DoubleSide });
    material.onBeforeCompile = (shader) => {
      shader.uniforms.uWind = this.wind = { value: 0 };
      shader.vertexShader = shader.vertexShader.replace('#include <common>', '#include <common>\nuniform float uWind;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nfloat sway = sin(uWind * 1.6 + instanceMatrix[3].x * 0.6 + instanceMatrix[3].z * 0.4) * 0.12 * position.y;\ntransformed.x += sway; transformed.z += sway * 0.6;');
    };
    this.grass = new THREE.InstancedMesh(blade, material, this.maxGrass);
    this.grass.count = 0; this.grass.frustumCulled = false; this.grass.receiveShadow = true;
    this.group.add(this.grass);
  }

  /** Re-scatter tufts within 120 m of the camera, density following grass fill. */
  updateGrass(camera, fill) {
    if (!fill) return;
    const cam = camera.position;
    if (this.lastFill === fill && Math.hypot(cam.x - this.lastCamera.x, cam.z - this.lastCamera.z) < 15) return;
    this.lastCamera.copy(cam); this.lastFill = fill;
    if (cam.y > 90) { this.grass.count = 0; return; }
    const { cell, depth, width } = this.relief, radius = 120, r0 = Math.max(0, Math.floor((cam.z - radius) / cell)), r1 = Math.min(depth - 1, Math.ceil((cam.z + radius) / cell));
    const c0 = Math.max(0, Math.floor((cam.x - radius) / cell)), c1 = Math.min(width - 1, Math.ceil((cam.x + radius) / cell));
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), p = new THREE.Vector3(), up = new THREE.Vector3(0, 1, 0);
    let n = 0;
    const perCell = 26;
    for (let r = r0; r <= r1 && n < this.maxGrass; r++) for (let c = c0; c <= c1 && n < this.maxGrass; c++) {
      const t = this.types[r * width + c];
      if (t === 0 || t === 4) continue;
      const f = fill[r * width + c] / 255, count = Math.round(perCell * f * (t === 3 ? 0.6 : 1));
      for (let k = 0; k < count && n < this.maxGrass; k++) {
        const x = (c + hash3(r, c, k * 3 + 1)) * cell, z = (r + hash3(r, c, k * 3 + 2)) * cell;
        if (Math.hypot(x - cam.x, z - cam.z) > radius) continue;
        const y = this.relief.heightAtMeters(x, z);
        const level = this.relief.waterLevelNear(z / cell, x / cell, 3);
        if (level !== null && y < level + 0.2) continue;
        p.set(x, y, z); q.setFromAxisAngle(up, hash3(r, c, k) * 6.28); s.setScalar(0.6 + hash3(r, c, k + 77) * 0.9);
        m.compose(p, q, s); this.grass.setMatrixAt(n++, m);
      }
    }
    this.grass.count = n; this.grass.instanceMatrix.needsUpdate = true;
  }

  update(seconds) { if (this.wind) this.wind.value = seconds; }
}
