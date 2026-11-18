// window.__sv: handles for automated checks (plan v3.2 section 9.5).
export function installHooks(app) {
  window.__sv = {
    stats() {
      const info = app.renderer.info.render, frame = app.animals.frame;
      return {
        fps: Math.round(app.fps), frameMs: +app.frameMs.toFixed(2), animals: frame ? frame.animalCount : 0,
        drawCalls: info.calls, triangles: info.triangles, sequence: frame ? frame.sequence : -1,
        step: frame ? frame.step : -1, generation: frame ? frame.generation : -1, quality: app.qualityName, lod: app.animals.lodCounts,
      };
    },
    setCamera(name) { app.rig.set(name); },
    setSpeed(x) { app.setSpeed(x); },
    setHour(h) { app.setHour(h); },
    pause() { app.client.control({ cmd: 'pause' }); },
    resume() { app.client.control({ cmd: 'resume' }); },
    stepOnce() { app.client.control({ cmd: 'step' }); },
    /** Puts the camera next to the first animal of a species (0 Lion .. 4 Gazelle); returns its id. */
    focusOn(species, distance = 14, height = 2.2) {
      const f = app.animals.frame; if (!f) return null;
      for (let i = 0; i < f.animalCount; i++) {
        if (f.species[i] !== species) continue;
        const cell = app.relief.cell, x = (f.col[i] + 0.5) * cell, z = (f.row[i] + 0.5) * cell, y = app.relief.heightAtMeters(x, z);
        app.rig.controls.target.set(x, y + 0.8, z); app.camera.position.set(x + distance * 0.5, y + height, z + distance); app.rig.controls.update();
        return f.id[i];
      }
      return null;
    },
    select(id) { app.ui.select(id); const f = app.animals.frame; const i = f ? app.ui.find(f, id) : -1; return i >= 0 ? app.ui.detailsOf(f, i) : null; },
    inspectText() { return app.ui.inspect.textContent; },
    overlay(kind) { app.ui.overlaySelect.value = kind; app.ui.overlay = kind; app.ui.applyOverlay(); },
    /** Like focusOn, but the animal must have the given executed-effect bit (1 drink, 4 graze) or the rest flag (bit 128 of flags when effect is 128). */
    focusEffect(species, bit, distance = 9, height = 1.6) {
      const f = app.animals.frame; if (!f) return null;
      for (let i = 0; i < f.animalCount; i++) {
        if (f.species[i] !== species) continue;
        const ok = bit === 128 ? (f.animalFlags[i] & 128) : (f.effects[i] & bit);
        if (!ok) continue;
        const cell = app.relief.cell, x = (f.col[i] + 0.5) * cell, z = (f.row[i] + 0.5) * cell, y = app.relief.heightAtMeters(x, z);
        app.rig.controls.target.set(x, y + 0.7, z); app.camera.position.set(x + distance * 0.6, y + height, z + distance); app.rig.controls.update();
        return f.id[i];
      }
      return null;
    },
    probe: {
      waterViolations: () => app.animals.waterViolations,
      maxJumpMeters: () => app.animals.maxJump,
    },
  };
}
