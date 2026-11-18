package savanna;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Display-only relief of the world (plan v3.2 V3-1, section 9.4). The kernel has
 * terrain types but no elevation; this class derives a deterministic height
 * field from the terrain seed and the terrain types. It never touches the
 * ecological random stream, and no ecological decision reads it.
 *
 * <p>The vertex grid is (2 depth + 1) x (2 width + 1); vertex (2r + 1, 2c + 1)
 * is the centre of cell (r, c). Heights are metres above an arbitrary datum.
 * Water bodies are the 4-connected components of {@code WATERHOLE}, numbered
 * from 1 in order of their first cell in row-major order.</p>
 *
 * <p>Invariants (checked by {@code TerrainReliefTests}):
 * R1 land within 3 cells of water is at least {@code W + 0.15} at its centre;
 * R2 water cell centres are at most {@code W - 0.25};
 * R3 every land-to-land step between 8-neighbour cells near water stays dry;
 * R4 the result is bit-for-bit reproducible and within [-10, 40] m.</p>
 */
public final class TerrainRelief
{
    private static final double[] WAVELENGTH_CELLS = {80.0, 30.0, 10.0};
    private static final double[] AMPLITUDE_M = {4.0, 1.5, 0.4};
    /** Land near a body (its box grown by this many cells) is kept above that body's level. */
    static final int NEAR_WATER_MARGIN_CELLS = 4;

    private final int depth;
    private final int width;
    private final int vDepth;
    private final int vWidth;
    private final float[] height;
    private final byte[] waterBody;
    private final float[] waterLevel;
    private final int[][] bodyBox;

    private TerrainRelief(int depth, int width, float[] height, byte[] waterBody,
                          float[] waterLevel, int[][] bodyBox)
    {
        this.depth = depth;
        this.width = width;
        vDepth = 2 * depth + 1;
        vWidth = 2 * width + 1;
        this.height = height;
        this.waterBody = waterBody;
        this.waterLevel = waterLevel;
        this.bodyBox = bodyBox;
    }

    public int getDepth() { return depth; }
    public int getWidth() { return width; }
    public int getVertexDepth() { return vDepth; }
    public int getVertexWidth() { return vWidth; }

    /** Number of water bodies; their ids are 1..count. */
    public int getBodyCount() { return waterLevel.length - 1; }

    /** Water level of body {@code id} (1-based), in metres. */
    public float getWaterLevel(int id) { return waterLevel[id]; }

    /** Water body id of a cell, 0 for land. */
    public int bodyAt(int row, int col) { return waterBody[row * width + col] & 0xFF; }

    /** Inclusive cell box {minRow, minCol, maxRow, maxCol} of body {@code id}. */
    public int[] getBodyBox(int id) { return bodyBox[id].clone(); }

    public float heightAtVertex(int vr, int vc) { return height[vr * vWidth + vc]; }

    public float centerHeight(int row, int col) { return height[(2 * row + 1) * vWidth + 2 * col + 1]; }

    /** Bilinear height at a position in cell units (row, col may be fractional; cell centres at +0.5). */
    public double heightAt(double row, double col)
    {
        double vr = Math.max(0.0, Math.min(vDepth - 1.001, row * 2.0));
        double vc = Math.max(0.0, Math.min(vWidth - 1.001, col * 2.0));
        int r0 = (int)vr;
        int c0 = (int)vc;
        double fr = vr - r0;
        double fc = vc - c0;
        double top = height[r0 * vWidth + c0] * (1 - fc) + height[r0 * vWidth + c0 + 1] * fc;
        double bottom = height[(r0 + 1) * vWidth + c0] * (1 - fc) + height[(r0 + 1) * vWidth + c0 + 1] * fc;
        return top * (1 - fr) + bottom * fr;
    }

    /** All heights, row-major, for export. */
    public float[] heights() { return height.clone(); }

    /** Water body ids, one byte per cell, row-major. */
    public byte[] bodies() { return waterBody.clone(); }

    public static TerrainRelief generate(TerrainMap terrain, long terrainSeed)
    {
        int depth = terrain.getDepth();
        int width = terrain.getWidth();
        int vDepth = 2 * depth + 1;
        int vWidth = 2 * width + 1;

        // 1. Water bodies: 4-connected components, numbered by first cell.
        byte[] body = new byte[depth * width];
        List<int[]> boxes = new ArrayList<>();
        boxes.add(new int[] {0, 0, 0, 0});
        int count = 0;
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                if(terrain.isPassableAt(row, col) || body[row * width + col] != 0) continue;
                count++;
                if(count > 255) throw new IllegalStateException("more than 255 water bodies");
                int[] box = {row, col, row, col};
                ArrayDeque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[] {row, col});
                body[row * width + col] = (byte)count;
                while(!queue.isEmpty()) {
                    int[] cell = queue.remove();
                    box[0] = Math.min(box[0], cell[0]);
                    box[1] = Math.min(box[1], cell[1]);
                    box[2] = Math.max(box[2], cell[0]);
                    box[3] = Math.max(box[3], cell[1]);
                    int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for(int[] step : steps) {
                        int nr = cell[0] + step[0];
                        int nc = cell[1] + step[1];
                        if(nr < 0 || nr >= depth || nc < 0 || nc >= width) continue;
                        if(terrain.isPassableAt(nr, nc) || body[nr * width + nc] != 0) continue;
                        body[nr * width + nc] = (byte)count;
                        queue.add(new int[] {nr, nc});
                    }
                }
                boxes.add(box);
            }
        }

        // 2. Base relief: three octaves of smooth value noise.
        Random random = new Random(terrainSeed ^ 0x5EED3DL);
        double[][] lattices = new double[3][];
        int[] latticeWidth = new int[3];
        int[] latticeDepth = new int[3];
        for(int k = 0; k < 3; k++) {
            latticeWidth[k] = (int)Math.ceil(width / WAVELENGTH_CELLS[k]) + 3;
            latticeDepth[k] = (int)Math.ceil(depth / WAVELENGTH_CELLS[k]) + 3;
            lattices[k] = new double[latticeWidth[k] * latticeDepth[k]];
            for(int i = 0; i < lattices[k].length; i++) lattices[k][i] = random.nextDouble() * 2.0 - 1.0;
        }
        float[] base = new float[vDepth * vWidth];
        for(int vr = 0; vr < vDepth; vr++) {
            for(int vc = 0; vc < vWidth; vc++) {
                double row = vr / 2.0;
                double col = vc / 2.0;
                double sum = 0.0;
                for(int k = 0; k < 3; k++) {
                    sum += AMPLITUDE_M[k] * smoothNoise(lattices[k], latticeWidth[k],
                        row / WAVELENGTH_CELLS[k], col / WAVELENGTH_CELLS[k]);
                }
                base[vr * vWidth + vc] = (float)sum;
            }
        }

        // 3. Terrain-type offset: mean of the touching cells, then a 5 x 5 box blur.
        float[] offset = new float[vDepth * vWidth];
        for(int vr = 0; vr < vDepth; vr++) {
            for(int vc = 0; vc < vWidth; vc++) {
                double total = 0.0;
                int cells = 0;
                for(int r = (vr - 1) / 2; r <= vr / 2; r++) {
                    for(int c = (vc - 1) / 2; c <= vc / 2; c++) {
                        if(r < 0 || r >= depth || c < 0 || c >= width) continue;
                        total += typeOffset(terrain.getTerrainAt(new Location(r, c)));
                        cells++;
                    }
                }
                offset[vr * vWidth + vc] = (float)(total / Math.max(1, cells));
            }
        }
        offset = boxBlur(offset, vDepth, vWidth, 2);

        // 4. Signed distance to the shoreline (cells; land positive, water negative).
        boolean[] water = new boolean[vDepth * vWidth];
        for(int vr = 0; vr < vDepth; vr++) {
            for(int vc = 0; vc < vWidth; vc++) {
                boolean all = true;
                for(int r = (vr - 1) / 2; r <= vr / 2 && all; r++) {
                    for(int c = (vc - 1) / 2; c <= vc / 2 && all; c++) {
                        if(r < 0 || r >= depth || c < 0 || c >= width) continue;
                        all = !terrain.isPassableAt(r, c);
                    }
                }
                water[vr * vWidth + vc] = all && count > 0;
            }
        }
        float[] signed = signedDistance(water, vDepth, vWidth);
        signed = gaussianBlur(signed, vDepth, vWidth, 0.7 * 2.0);
        // The blur can move the zero crossing; pin cell centres so R2 and land/water signs hold.
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                int index = (2 * row + 1) * vWidth + 2 * col + 1;
                if(terrain.isPassableAt(row, col)) signed[index] = Math.max(signed[index], 0.01f);
                else signed[index] = Math.min(signed[index], -0.01f);
            }
        }

        // Owner body of each vertex: nearest water vertex (multi-source BFS).
        byte[] owner = new byte[vDepth * vWidth];
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        for(int row = 0; row < depth; row++) {
            for(int col = 0; col < width; col++) {
                int id = body[row * width + col] & 0xFF;
                if(id == 0) continue;
                for(int vr = 2 * row; vr <= 2 * row + 2; vr++) {
                    for(int vc = 2 * col; vc <= 2 * col + 2; vc++) {
                        if(owner[vr * vWidth + vc] == 0) {
                            owner[vr * vWidth + vc] = (byte)id;
                            frontier.add(new int[] {vr, vc});
                        }
                    }
                }
            }
        }
        while(!frontier.isEmpty()) {
            int[] cell = frontier.remove();
            for(int dr = -1; dr <= 1; dr++) {
                for(int dc = -1; dc <= 1; dc++) {
                    int nr = cell[0] + dr;
                    int nc = cell[1] + dc;
                    if(nr < 0 || nr >= vDepth || nc < 0 || nc >= vWidth) continue;
                    if(owner[nr * vWidth + nc] != 0) continue;
                    owner[nr * vWidth + nc] = owner[cell[0] * vWidth + cell[1]];
                    frontier.add(new int[] {nr, nc});
                }
            }
        }

        // 5. Water level per body: lowest base relief on its shore, minus 0.2 m.
        float[] level = new float[count + 1];
        for(int id = 1; id <= count; id++) {
            float lowest = Float.POSITIVE_INFINITY;
            for(int i = 0; i < base.length; i++) {
                if((owner[i] & 0xFF) == id && signed[i] >= 0.0f && signed[i] <= 1.0f) {
                    lowest = Math.min(lowest, base[i] + offset[i]);
                }
            }
            if(Float.isInfinite(lowest)) lowest = 0.0f;
            level[id] = lowest - 0.2f;
        }

        // 6. Compose.
        float[] out = new float[vDepth * vWidth];
        for(int i = 0; i < out.length; i++) {
            int id = owner[i] & 0xFF;
            float w = id == 0 ? 0.0f : level[id];
            double d = signed[i];
            double dry = base[i] + offset[i];
            double h;
            if(id == 0) h = dry;
            else if(d < 0.0) h = w + Math.max(-2.5, 0.6 * d);
            else if(d <= 1.5) h = w + 0.35 * d;
            else if(d <= 6.0) {
                double t = smoothstep((d - 1.5) / 4.5);
                h = Math.max(w + 0.5, (w + 0.35 * d) * (1.0 - t) + dry * t);
            }
            else h = dry;
            out[i] = (float)Math.max(-10.0, Math.min(40.0, h));
        }

        // 7. Enforce the invariants where they matter (near water), a few passes at most.
        for(int pass = 0; pass < 5; pass++) {
            boolean changed = false;
            for(int id = 1; id <= count; id++) {
                int[] box = boxes.get(id);
                int r0 = Math.max(0, box[0] - NEAR_WATER_MARGIN_CELLS);
                int r1 = Math.min(depth - 1, box[2] + NEAR_WATER_MARGIN_CELLS);
                int c0 = Math.max(0, box[1] - NEAR_WATER_MARGIN_CELLS);
                int c1 = Math.min(width - 1, box[3] + NEAR_WATER_MARGIN_CELLS);
                for(int vr = 2 * r0; vr <= 2 * r1 + 2; vr++) {
                    for(int vc = 2 * c0; vc <= 2 * c1 + 2; vc++) {
                        int index = vr * vWidth + vc;
                        if(water[index]) continue;
                        float floor = level[id] + 0.05f;
                        if(out[index] < floor) {
                            out[index] = floor;
                            changed = true;
                        }
                    }
                }
            }
            for(int row = 0; row < depth; row++) {
                for(int col = 0; col < width; col++) {
                    int index = (2 * row + 1) * vWidth + 2 * col + 1;
                    int id = owner[index] & 0xFF;
                    if(id == 0) continue;
                    if(terrain.isPassableAt(row, col)) {
                        if(signed[index] <= 3.0f && out[index] < level[id] + 0.15f) {
                            out[index] = level[id] + 0.15f;
                            changed = true;
                        }
                    }
                    else if(out[index] > level[id] - 0.25f) {
                        out[index] = level[id] - 0.25f;
                        changed = true;
                    }
                }
            }
            if(!changed) break;
        }
        int[][] boxArray = new int[boxes.size()][];
        for(int i = 0; i < boxArray.length; i++) boxArray[i] = boxes.get(i);
        return new TerrainRelief(depth, width, out, body, level, boxArray);
    }

    private static double typeOffset(TerrainType type)
    {
        switch(type) {
            case DRY_SOIL: return 0.8;
            case BUSH: return 0.6;
            case OPEN_PLAIN: return 0.3;
            default: return 0.0;
        }
    }

    private static double smoothstep(double t)
    {
        double x = Math.max(0.0, Math.min(1.0, t));
        return x * x * (3.0 - 2.0 * x);
    }

    private static double smoothNoise(double[] lattice, int latticeWidth, double row, double col)
    {
        int r0 = (int)Math.floor(row);
        int c0 = (int)Math.floor(col);
        double fr = smoothstep(row - r0);
        double fc = smoothstep(col - c0);
        double a = lattice[r0 * latticeWidth + c0];
        double b = lattice[r0 * latticeWidth + c0 + 1];
        double c = lattice[(r0 + 1) * latticeWidth + c0];
        double d = lattice[(r0 + 1) * latticeWidth + c0 + 1];
        return (a * (1 - fc) + b * fc) * (1 - fr) + (c * (1 - fc) + d * fc) * fr;
    }

    private static float[] boxBlur(float[] in, int rows, int cols, int radius)
    {
        float[] horizontal = new float[in.length];
        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                double sum = 0.0;
                for(int k = -radius; k <= radius; k++) {
                    sum += in[r * cols + Math.max(0, Math.min(cols - 1, c + k))];
                }
                horizontal[r * cols + c] = (float)(sum / (2 * radius + 1));
            }
        }
        float[] out = new float[in.length];
        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                double sum = 0.0;
                for(int k = -radius; k <= radius; k++) {
                    sum += horizontal[Math.max(0, Math.min(rows - 1, r + k)) * cols + c];
                }
                out[r * cols + c] = (float)(sum / (2 * radius + 1));
            }
        }
        return out;
    }

    private static float[] gaussianBlur(float[] in, int rows, int cols, double sigmaVertices)
    {
        int radius = Math.max(1, (int)Math.ceil(sigmaVertices * 3.0));
        double[] kernel = new double[2 * radius + 1];
        double total = 0.0;
        for(int k = -radius; k <= radius; k++) {
            kernel[k + radius] = Math.exp(-(k * k) / (2.0 * sigmaVertices * sigmaVertices));
            total += kernel[k + radius];
        }
        for(int k = 0; k < kernel.length; k++) kernel[k] /= total;
        float[] horizontal = new float[in.length];
        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                double sum = 0.0;
                for(int k = -radius; k <= radius; k++) {
                    sum += kernel[k + radius] * in[r * cols + Math.max(0, Math.min(cols - 1, c + k))];
                }
                horizontal[r * cols + c] = (float)sum;
            }
        }
        float[] out = new float[in.length];
        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                double sum = 0.0;
                for(int k = -radius; k <= radius; k++) {
                    sum += kernel[k + radius] * horizontal[Math.max(0, Math.min(rows - 1, r + k)) * cols + c];
                }
                out[r * cols + c] = (float)sum;
            }
        }
        return out;
    }

    /** Two-pass 3-4 chamfer transform, in cells: positive on land, negative in water. */
    private static float[] signedDistance(boolean[] water, int rows, int cols)
    {
        float[] toWater = chamfer(water, rows, cols, true);
        float[] toLand = chamfer(water, rows, cols, false);
        float[] out = new float[water.length];
        for(int i = 0; i < out.length; i++) {
            out[i] = water[i] ? -toLand[i] : toWater[i];
        }
        return out;
    }

    /** Distance (in cells) from each vertex to the nearest vertex whose mask equals {@code target}. */
    private static float[] chamfer(boolean[] mask, int rows, int cols, boolean target)
    {
        final float inf = 1e9f;
        float[] dist = new float[mask.length];
        for(int i = 0; i < dist.length; i++) dist[i] = mask[i] == target ? 0.0f : inf;
        for(int r = 0; r < rows; r++) {
            for(int c = 0; c < cols; c++) {
                float best = dist[r * cols + c];
                if(c > 0) best = Math.min(best, dist[r * cols + c - 1] + 3.0f);
                if(r > 0) {
                    best = Math.min(best, dist[(r - 1) * cols + c] + 3.0f);
                    if(c > 0) best = Math.min(best, dist[(r - 1) * cols + c - 1] + 4.0f);
                    if(c < cols - 1) best = Math.min(best, dist[(r - 1) * cols + c + 1] + 4.0f);
                }
                dist[r * cols + c] = best;
            }
        }
        for(int r = rows - 1; r >= 0; r--) {
            for(int c = cols - 1; c >= 0; c--) {
                float best = dist[r * cols + c];
                if(c < cols - 1) best = Math.min(best, dist[r * cols + c + 1] + 3.0f);
                if(r < rows - 1) {
                    best = Math.min(best, dist[(r + 1) * cols + c] + 3.0f);
                    if(c < cols - 1) best = Math.min(best, dist[(r + 1) * cols + c + 1] + 4.0f);
                    if(c > 0) best = Math.min(best, dist[(r + 1) * cols + c - 1] + 4.0f);
                }
                dist[r * cols + c] = best;
            }
        }
        // 3 per axis step on the vertex grid = 1 vertex; two vertices per cell.
        for(int i = 0; i < dist.length; i++) dist[i] = dist[i] >= inf ? inf : dist[i] / 6.0f;
        return dist;
    }
}
