// Procedural sky, sun and moon light from the simulation hour; stars at night.
import * as THREE from 'three';
import { Sky } from '../../vendor/examples/jsm/objects/Sky.js';

export class SkyRig {
  constructor(scene, renderer) {
    this.scene = scene;
    this.sky = new Sky(); this.sky.scale.setScalar(20000); scene.add(this.sky);
    const u = this.sky.material.uniforms;
    u.turbidity.value = 4; u.rayleigh.value = 1.6; u.mieCoefficient.value = 0.004; u.mieDirectionalG.value = 0.8;
    this.sun = new THREE.DirectionalLight(0xffffff, 2.2);
    this.sun.castShadow = false; scene.add(this.sun); scene.add(this.sun.target);
    this.moon = new THREE.DirectionalLight(0x8fa8ff, 0.0); scene.add(this.moon);
    this.hemi = new THREE.HemisphereLight(0xbcd4ff, 0x6b5a3c, 0.7); scene.add(this.hemi);
    this.fog = new THREE.Fog(0xcfe0f0, 800, 5200); scene.fog = this.fog;
    renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 0.9;
    const stars = new Float32Array(900 * 3);
    for (let i = 0; i < 900; i++) {
      const a = Math.random() * Math.PI * 2, e = Math.acos(Math.random()), r = 9000;
      stars[3 * i] = Math.sin(e) * Math.cos(a) * r; stars[3 * i + 1] = Math.cos(e) * r; stars[3 * i + 2] = Math.sin(e) * Math.sin(a) * r;
    }
    const geometry = new THREE.BufferGeometry(); geometry.setAttribute('position', new THREE.BufferAttribute(stars, 3));
    this.stars = new THREE.Points(geometry, new THREE.PointsMaterial({ color: 0xffffff, size: 22, sizeAttenuation: false, transparent: true, opacity: 0, fog: false }));
    scene.add(this.stars);
    this.weather = { dim: 1, haze: 1, warm: 0 };
  }

  /** hourFloat: 0..24, continuous (hour + progress inside the step). */
  update(hourFloat, target) {
    const day = (hourFloat - 6) / 12 * Math.PI;              // 6:00 sunrise, 18:00 sunset
    const elevation = Math.sin(day);                         // -1..1
    const azimuth = (hourFloat / 24) * Math.PI * 2;
    const sunDir = new THREE.Vector3(Math.cos(azimuth) * Math.cos(day) * 0.6 + 0.4, Math.max(-0.4, elevation), Math.sin(azimuth) * 0.7 - 0.3).normalize();
    const u = this.sky.material.uniforms;
    u.sunPosition.value.copy(sunDir);
    const dayLight = Math.max(0, Math.min(1, elevation * 3 + 0.25));
    const twilight = Math.max(0, 1 - Math.abs(elevation) * 4);
    this.sun.position.copy(target).addScaledVector(sunDir, 900); this.sun.target.position.copy(target);
    this.sun.intensity = 2.4 * dayLight * this.weather.dim;
    this.sun.color.setHSL(0.09 + 0.03 * (1 - dayLight), 0.55 * (1 - dayLight) + 0.2, 0.93 - 0.18 * twilight);
    this.moon.position.copy(target).addScaledVector(sunDir, -600); this.moon.position.y = Math.abs(this.moon.position.y) + 300;
    this.moon.intensity = 0.55 * (1 - dayLight);
    this.hemi.intensity = 0.28 + 0.62 * dayLight;
    this.stars.material.opacity = Math.max(0, 1 - dayLight * 2.4) * 0.9 * this.weather.haze;
    this.stars.visible = this.stars.material.opacity > 0.01;
    const fogDay = new THREE.Color(0xcfe0f0), fogNight = new THREE.Color(0x0b1424), fogDusk = new THREE.Color(0xe8b48a);
    this.fog.color.copy(fogNight).lerp(fogDay, dayLight).lerp(fogDusk, twilight * 0.55 * dayLight);
    this.fog.far = 5200 / this.weather.haze;
    this.sky.material.uniforms.rayleigh.value = 1.2 + 1.2 * (1 - dayLight) + this.weather.warm;
    this.lastDay = dayLight;
    return { dayLight, sunDir };
  }
}
