// Smooth weather state (clear, rain, fog, drought) driving fog, light and a rain particle field.
import * as THREE from 'three';

const TARGETS = {          // code -> { dim, haze, warm, fogNear, rain }
  0: { dim: 1.0, haze: 1.0, warm: 0.0, rain: 0 },
  1: { dim: 0.55, haze: 2.2, warm: 0.0, rain: 1 },
  2: { dim: 0.7, haze: 6.0, warm: 0.0, rain: 0 },
  3: { dim: 1.05, haze: 1.5, warm: 1.4, rain: 0 },
};

export class Weather {
  constructor(scene) {
    this.state = { dim: 1, haze: 1, warm: 0, rain: 0 }; this.target = TARGETS[0];
    const n = 4000, pos = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) { pos[3 * i] = (Math.random() - 0.5) * 160; pos[3 * i + 1] = Math.random() * 60; pos[3 * i + 2] = (Math.random() - 0.5) * 160; }
    const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    this.rain = new THREE.Points(g, new THREE.PointsMaterial({ color: 0xbfd0e0, size: 2, sizeAttenuation: false, transparent: true, opacity: 0, fog: false }));
    this.rain.frustumCulled = false; scene.add(this.rain);
  }
  setCode(code) { this.target = TARGETS[code] || TARGETS[0]; }
  update(dt, camera, sky) {
    const k = 1 - Math.exp(-dt / 1.2);
    for (const key of ['dim', 'haze', 'warm', 'rain']) this.state[key] += (this.target[key] - this.state[key]) * k;
    sky.weather.dim = this.state.dim; sky.weather.haze = this.state.haze; sky.weather.warm = this.state.warm;
    this.rain.visible = this.state.rain > 0.02;
    if (this.rain.visible) {
      this.rain.material.opacity = 0.5 * this.state.rain;
      const a = this.rain.geometry.getAttribute('position');
      for (let i = 0; i < a.count; i++) { let y = a.array[3 * i + 1] - 40 * dt; if (y < 0) y += 60; a.array[3 * i + 1] = y; }
      a.needsUpdate = true;
      this.rain.position.set(camera.position.x, Math.max(0, camera.position.y - 30), camera.position.z);
    }
  }
}
