public class VisualWaterMask
{
    private final TerrainMap terrain;
    private final int gridWidth;
    private final int gridHeight;
    private final int pixelWidth;
    private final int pixelHeight;
    private final VisualGridGeometry geometry;

    public VisualWaterMask(TerrainMap terrain, int pixelWidth, int pixelHeight)
    {
        this.terrain = terrain;
        gridWidth = terrain.getWidth();
        gridHeight = terrain.getDepth();
        this.pixelWidth = Math.max(1, pixelWidth);
        this.pixelHeight = Math.max(1, pixelHeight);
        geometry = VisualGridGeometry.forTerrain(terrain, this.pixelWidth,
                                                 this.pixelHeight);
    }

    public boolean ordinaryAnimalFootprintTouchesWater(Location location)
    {
        return ordinaryAnimalFootprintTouchesWater(location, null);
    }

    public boolean ordinaryAnimalFootprintTouchesWater(Location location,
                                                       SavannahAnimal animal)
    {
        return VisualFootprint.ordinaryTouchesWater(location, animal,
                                                    geometry, terrain);
    }

    public boolean isWaterPixel(int x, int y)
    {
        return geometry.isWaterPixel(terrain, x, y);
    }
}
