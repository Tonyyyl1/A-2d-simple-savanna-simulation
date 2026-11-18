// Browser client: world, sky, animals and the frame link.
import * as THREE from 'three';
import { CONFIG } from './config.js';
import { FrameClient } from './net/frameClient.js';
import { Relief } from './world/relief.js';
import { TerrainMesh } from './world/terrain.js';
import { WaterSurfaces } from './world/water.js';
import { SkyRig } from './world/sky.js';
import { Vegetation } from './world/vegetation.js';
import { Weather } from './world/weather.js';
import { CameraRig } from './camera/cameraRig.js';
import { AnimalRenderer } from './animals/animalRenderer.js';
import { installHooks } from './debug/hooks.js';
import { UI } from './ui/ui.js';

const params = new URLSearchParams(location.search);
const status = document.getElementById('status');
const app = { qualityName: params.get('quality') || 'medium', fps: 0, frameMs: 0, speed: parseFloat(params.get('speed') || '1') };
const quality = CONFIG.quality[app.qualityName] || CONFIG.quality.medium;

const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(devicePixelRatio, quality.pixelRatio));
renderer.setSize(innerWidth, innerHeight);
renderer.shadowMap.enabled = quality.shadow > 0;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(55, innerWidth / innerHeight, 0.3, 30000);
camera.position.set(900, 300, 700);
app.ring = null; app.renderer = renderer; app.scene = scene; app.camera = camera;
window.addEventListener('resize', () => { renderer.setSize(innerWidth, innerHeight); camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); });

let world = null, relief = null, terrain = null, water = null, sky = null, vegetation = null, weather = null;
let forcedHour = params.get('hour') !== null ? parseFloat(params.get('hour')) : null;
let hourFloat = 6, frameArrival = 0, latest = null, grassFill = null;
const stepMs = () => CONFIG.stepSeconds * 1000 / app.speed;

app.setSpeed = (x) => { app.speed = x; app.client.control({ cmd: 'setSpeed', stepsPerSecond: x / CONFIG.stepSeconds }); };

async function buildWorld(w, terrainTypes) {
  const base = '';
  const [heightBuffer, bodyBuffer] = await Promise.all([
    fetch(base + '/api/world/height.bin').then((r) => r.arrayBuffer()),
    fetch(base + '/api/world/waterbody.bin').then((r) => r.arrayBuffer()),
  ]);
  for (const old of [terrain, water, vegetation, app.animals]) if (old) scene.remove(old.group);
  world = w;
  relief = new Relief({ heights: new Float32Array(heightBuffer), bodies: new Uint8Array(bodyBuffer), depth: w.terrain.depth, width: w.terrain.width, waterLevels: w.terrain.waterLevels, cellMeters: w.terrain.cellMeters || CONFIG.cellMeters });
  terrain = new TerrainMesh(relief, terrainTypes); scene.add(terrain.group);
  water = new WaterSurfaces(relief); scene.add(water.group);
  if (!sky) { sky = new SkyRig(scene, renderer); weather = new Weather(scene); }
  vegetation = new Vegetation(relief, terrainTypes, w.config.terrainSeed | 0, app.qualityName); scene.add(vegetation.group);
  app.animals = new AnimalRenderer(scene, relief, terrainTypes);
  app.relief = relief; app.terrainMesh = terrain; app.terrainTypes = terrainTypes;
  if (!app.ui) app.ui = new UI(app);
  app.ui.setWorld(w); app.ui.miniBase = null;
  if (!app.ring) { app.ring = new THREE.Mesh(new THREE.RingGeometry(1.4, 1.8, 40).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0xffe066, transparent: true, opacity: 0.9, depthTest: false })); app.ring.renderOrder = 10; app.ring.visible = false; scene.add(app.ring); }
  if (!app.rig) { app.rig = new CameraRig(camera, renderer.domElement, relief); }
  const box = relief.boxes()[0];
  const focus = box && box.r1 >= 0 ? new THREE.Vector3((box.c1 + 7) * relief.cell, 0, (box.r0 + box.r1) / 2 * relief.cell) : null;
  app.rig.relief = relief; app.rig.set(params.get('camera') || 'oblique', focus);
  if (sky.sun.castShadow !== (quality.shadow > 0)) sky.sun.castShadow = quality.shadow > 0;
  if (quality.shadow > 0) {
    const s = sky.sun.shadow; s.mapSize.set(quality.shadow, quality.shadow);
    s.camera.left = -180; s.camera.right = 180; s.camera.top = 180; s.camera.bottom = -180; s.camera.near = 10; s.camera.far = 2500; s.bias = -0.0004;
  }
}

app.client = new FrameClient({
  async onWorld(w, terrainTypes) { await buildWorld(w, terrainTypes); status.textContent = 'world ready'; },
  onFrame(frame) {
    latest = frame; frameArrival = performance.now();
    if (!relief) return;
    app.animals.setFrame(frame, frameArrival);
    app.ui.onFrame(frame);
    if (weather) weather.setCode(frame.weather);
    if (frame.grass) { grassFill = frame.grass; terrain.setGrass(frame.grass); }
    hourFloat = frame.hour;
    status.textContent = `step ${frame.step}  ${String(frame.hour).padStart(2, '0')}:00   ${frame.speciesCounts.map((n, i) => world.species[i].name + ' ' + n).join('  ')}`;
  },
  onStatus(text) { status.style.display = text === 'ok' ? 'none' : 'block'; if (text !== 'ok') status.textContent = text; },
});
app.setSpeed(app.speed);
app.setHour = (h) => { forcedHour = h; };
installHooks(app);
if (params.get('paused') === '1') setTimeout(() => app.client.control({ cmd: 'pause' }), 800);
app.client.start();

let last = performance.now(), fpsAcc = 0, fpsCount = 0;
function frameLoop(now) {
  const dt = Math.min(0.1, (now - last) / 1000); last = now;
  fpsAcc += dt; fpsCount++; if (fpsAcc >= 0.5) { app.fps = fpsCount / fpsAcc; app.frameMs = fpsAcc / fpsCount * 1000; fpsAcc = 0; fpsCount = 0; }
  if (relief) {
    const progress = latest ? Math.min(1, (now - frameArrival) / stepMs()) : 0, t = now / 1000;
    sky.update(forcedHour !== null ? forcedHour : hourFloat + progress, app.rig.controls.target);
    weather.update(dt, camera, sky); water.update(t, scene.fog.color, sky.lastDay); vegetation.update(t);
    if (grassFill) vegetation.updateGrass(camera, grassFill);
    scene.fog.near = 800 / sky.weather.haze;
    app.animals.update(now, stepMs(), camera.position);
    app.ui.tick(now);
    if (app.ui.selected !== null) {
      const pos = app.animals.positionOf(app.ui.selected);
      if (pos) {
        const y = relief.heightAtMeters(pos[0], pos[1]) + 0.15; app.ring.position.set(pos[0], y, pos[1]); app.ring.visible = true;
        if (app.ui.follow) { const d = new THREE.Vector3(pos[0], y, pos[1]).sub(app.rig.controls.target); app.rig.controls.target.add(d); camera.position.add(d); }
      } else app.ring.visible = false;
    } else if (app.ring) app.ring.visible = false;
    app.rig.update(dt);
    if (sky.sun.castShadow) { sky.sun.position.copy(app.rig.controls.target).addScaledVector(sky.sun.position.clone().sub(sky.sun.target.position).normalize(), 900); sky.sun.target.position.copy(app.rig.controls.target); }
    renderer.render(scene, camera);
  }
  requestAnimationFrame(frameLoop);
}
requestAnimationFrame(frameLoop);
