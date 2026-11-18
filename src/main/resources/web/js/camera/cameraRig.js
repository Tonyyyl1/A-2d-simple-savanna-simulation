// Orbit camera with WASD panning that never goes under the terrain.
import * as THREE from 'three';
import { OrbitControls } from '../../vendor/examples/jsm/controls/OrbitControls.js';

export class CameraRig {
  constructor(camera, dom, relief) {
    this.camera = camera; this.relief = relief;
    this.controls = new OrbitControls(camera, dom);
    this.controls.enableDamping = true; this.controls.dampingFactor = 0.08;
    this.controls.maxPolarAngle = Math.PI * 0.495; this.controls.minDistance = 3; this.controls.maxDistance = 2500;
    this.keys = new Set();
    window.addEventListener('keydown', (e) => this.keys.add(e.key.toLowerCase()));
    window.addEventListener('keyup', (e) => this.keys.delete(e.key.toLowerCase()));
    this.center = new THREE.Vector3(relief.width * relief.cell / 2, 0, relief.depth * relief.cell / 2);
  }
  set(name, focus) {
    const c = focus || this.center, ground = this.relief.heightAtMeters(c.x, c.z);
    this.controls.target.set(c.x, ground, c.z);
    if (name === 'aerial') this.camera.position.set(c.x, ground + 320, c.z + 280);
    else if (name === 'oblique') this.camera.position.set(c.x - 60, ground + 40, c.z + 95);
    else this.camera.position.set(c.x, ground + 1.7, c.z + 30);
    this.controls.update();
  }
  update(dt) {
    const speed = this.camera.position.y * 0.9 * dt + 6 * dt, v = new THREE.Vector3(), forward = new THREE.Vector3();
    this.camera.getWorldDirection(forward); forward.y = 0; forward.normalize();
    const right = new THREE.Vector3().crossVectors(forward, new THREE.Vector3(0, 1, 0));
    if (this.keys.has('w')) v.add(forward); if (this.keys.has('s')) v.sub(forward);
    if (this.keys.has('d')) v.add(right); if (this.keys.has('a')) v.sub(right);
    if (v.lengthSq() > 0) { v.normalize().multiplyScalar(speed); this.camera.position.add(v); this.controls.target.add(v); }
    this.controls.update();
    const ground = this.relief.heightAtMeters(this.camera.position.x, this.camera.position.z) + 1.2;
    if (this.camera.position.y < ground) this.camera.position.y = ground;
  }
}
