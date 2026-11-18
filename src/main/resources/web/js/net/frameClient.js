// Pulls frames from /api/frames, keeps the generation straight and hands decoded frames to a callback.
import { decodeFrame, splitFrames } from './protocol.js';

export class FrameClient {
  constructor({ base = '', onWorld, onFrame, onStatus }) {
    this.base = base; this.onWorld = onWorld; this.onFrame = onFrame; this.onStatus = onStatus || (() => {});
    this.generation = -1; this.after = -1; this.running = false;
  }

  async loadWorld() {
    const world = await (await fetch(this.base + '/api/world')).json();
    const terrain = new Uint8Array(await (await fetch(this.base + '/api/world/terrain.bin')).arrayBuffer());
    this.generation = world.runGeneration; this.after = -1;
    this.onWorld(world, terrain);
    return world;
  }

  async start() {
    this.running = true;
    await this.loadWorld();
    while (this.running) {
      try {
        const url = `${this.base}/api/frames?gen=${this.generation}&after=${this.after}&max=8&waitMs=4000`;
        const response = await fetch(url);
        if (response.status === 204) continue;
        if (response.status === 409) { await this.loadWorld(); continue; }
        if (!response.ok) throw new Error('frames ' + response.status);
        for (const raw of splitFrames(await response.arrayBuffer())) {
          const frame = decodeFrame(raw);
          this.after = Math.max(this.after, frame.sequence);
          this.onFrame(frame);
        }
        this.onStatus('ok');
      } catch (error) {
        this.onStatus('error: ' + error.message);
        await new Promise((r) => setTimeout(r, 1000));
      }
    }
  }

  stop() { this.running = false; }

  async control(command) {
    const response = await fetch(this.base + '/api/control', { method: 'POST', body: JSON.stringify(command) });
    return response.json();
  }
}
