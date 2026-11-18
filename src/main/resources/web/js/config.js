// Scale and rendering defaults. Cell size and step time were chosen by the user at the end of V0 (2026-11-11).
export const CONFIG = {
  cellMeters: 5,
  stepSeconds: 2,            // seconds of display time per simulation step at 1x
  chunkCells: 32,            // terrain chunk edge in cells
  quality: {
    low:    { shadow: 0,    pixelRatio: 1,   grassInstances: 20000,  treeDensity: 0.4 },
    medium: { shadow: 2048, pixelRatio: 1,   grassInstances: 60000,  treeDensity: 0.7 },
    high:   { shadow: 4096, pixelRatio: 1.5, grassInstances: 150000, treeDensity: 1.0 },
  },
  terrainColors: {            // by terrain code: 0 water bed, 1 grassland, 2 bush, 3 open plain, 4 dry soil
    0: [0.30, 0.27, 0.20], 1: [0.34, 0.50, 0.17], 2: [0.22, 0.36, 0.14], 3: [0.62, 0.54, 0.30], 4: [0.56, 0.43, 0.28],
  },
  grassDry: [0.62, 0.55, 0.32],
  speciesColors: [0xda841f, 0xe6c22f, 0xe8e8e8, 0x48443e, 0xc63037],
};
