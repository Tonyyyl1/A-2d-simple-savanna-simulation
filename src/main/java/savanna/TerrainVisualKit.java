package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned description of the TV1 terrain-art kit.
 *
 * <p>This class deliberately describes assets without loading them.  TV1 is a
 * mask-composition prototype, so the paths below are the future packaged
 * locations rather than a claim that image files already exist.  TV2 owns
 * copying those files to the runtime tree and checking their hashes.  Keeping
 * that boundary here lets callers review scale, alpha and placement policy
 * before an image resource can affect a release artifact.</p>
 */
public final class TerrainVisualKit
{
    public static final String TV1_PROTOTYPE_VERSION = "TV1-PROTOTYPE-1";
    public static final String TV2_PACKAGED_VERSION = "TV2-PACKAGED-1";

    public enum LayerRole
    {
        SURFACE,
        TRANSITION,
        SHORELINE,
        DECORATION
    }

    /** How a material may be fitted to a logical region. */
    public enum PlacementPolicy
    {
        /** Seamless, world-anchored tiling.  It must still be clipped. */
        WORLD_TILE,
        /** A strip follows a boundary; it is not a nine-slice fill. */
        CONTOUR_BAND,
        /** A transparent, uniformly scaled patch with a declared anchor. */
        UNIFORM_DECORATION
    }

    public enum Anchor
    {
        WORLD_ORIGIN,
        CELL_CENTER,
        BOUNDARY_TANGENT
    }

    /**
     * Immutable, reviewable art contract.  {@code plannedAssetPath} is a
     * package-relative path for TV2; a descriptor alone does not mean the
     * resource is available at runtime.
     */
    public static final class MaterialDescriptor
    {
        private final String id;
        private final TerrainType terrainType;
        private final LayerRole role;
        private final String plannedAssetPath;
        private final int pixelWidth;
        private final int pixelHeight;
        private final double worldWidthCells;
        private final double worldHeightCells;
        private final Anchor anchor;
        private final PlacementPolicy placementPolicy;
        private final boolean rotationAllowed;
        private final int transparentInsetPixels;
        private final String resourceSha256;

        private MaterialDescriptor(String id, TerrainType terrainType,
                                   LayerRole role, String plannedAssetPath,
                                   int pixelWidth, int pixelHeight,
                                   double worldWidthCells,
                                   double worldHeightCells, Anchor anchor,
                                   PlacementPolicy placementPolicy,
                                   boolean rotationAllowed,
                                   int transparentInsetPixels,
                                   String resourceSha256)
        {
            this.id = requireText(id, "id");
            this.terrainType = terrainType;
            this.role = role == null ? LayerRole.SURFACE : role;
            this.plannedAssetPath = requireText(plannedAssetPath,
                                                 "plannedAssetPath");
            this.pixelWidth = positive(pixelWidth, "pixelWidth");
            this.pixelHeight = positive(pixelHeight, "pixelHeight");
            if(worldWidthCells <= 0.0 || worldHeightCells <= 0.0) {
                throw new IllegalArgumentException("World dimensions must be positive.");
            }
            this.worldWidthCells = worldWidthCells;
            this.worldHeightCells = worldHeightCells;
            this.anchor = anchor == null ? Anchor.WORLD_ORIGIN : anchor;
            this.placementPolicy = placementPolicy == null
                ? PlacementPolicy.WORLD_TILE : placementPolicy;
            this.rotationAllowed = rotationAllowed;
            this.transparentInsetPixels = Math.max(0, transparentInsetPixels);
            this.resourceSha256 = resourceSha256;
        }

        public String getId() { return id; }
        public TerrainType getTerrainType() { return terrainType; }
        public LayerRole getRole() { return role; }
        public String getPlannedAssetPath() { return plannedAssetPath; }
        public int getPixelWidth() { return pixelWidth; }
        public int getPixelHeight() { return pixelHeight; }
        public double getWorldWidthCells() { return worldWidthCells; }
        public double getWorldHeightCells() { return worldHeightCells; }
        public Anchor getAnchor() { return anchor; }
        public PlacementPolicy getPlacementPolicy() { return placementPolicy; }
        public boolean isRotationAllowed() { return rotationAllowed; }
        public int getTransparentInsetPixels() { return transparentInsetPixels; }
        public String getResourceSha256() { return resourceSha256; }

        public boolean isPackagedResource()
        {
            return resourceSha256 != null;
        }
    }

    private final String version;
    private final Map<String, MaterialDescriptor> byId;
    private final Map<TerrainType, MaterialDescriptor> surfaceByTerrain;

    private TerrainVisualKit(String version, List<MaterialDescriptor> descriptors)
    {
        this.version = requireText(version, "version");
        Map<String, MaterialDescriptor> ids = new LinkedHashMap<>();
        Map<TerrainType, MaterialDescriptor> surfaces =
            new EnumMap<>(TerrainType.class);
        for(MaterialDescriptor descriptor : descriptors) {
            if(ids.put(descriptor.getId(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate terrain material: " +
                                                   descriptor.getId());
            }
            if(descriptor.getRole() == LayerRole.SURFACE &&
               descriptor.getTerrainType() != null) {
                if(surfaces.put(descriptor.getTerrainType(), descriptor) != null) {
                    throw new IllegalArgumentException("Duplicate surface for " +
                                                       descriptor.getTerrainType());
                }
            }
        }
        for(TerrainType type : TerrainType.values()) {
            if(!surfaces.containsKey(type)) {
                throw new IllegalArgumentException("Missing surface material for " + type);
            }
        }
        this.byId = Collections.unmodifiableMap(ids);
        this.surfaceByTerrain = Collections.unmodifiableMap(surfaces);
    }

    /** The complete descriptor-only kit supplied by TV1. */
    public static TerrainVisualKit tv1Prototype()
    {
        List<MaterialDescriptor> descriptors = new ArrayList<>();
        descriptors.add(surface("grassland", TerrainType.GRASSLAND));
        descriptors.add(surface("open-plain", TerrainType.OPEN_PLAIN));
        descriptors.add(surface("dry-soil", TerrainType.DRY_SOIL));
        descriptors.add(surface("bush", TerrainType.BUSH));
        descriptors.add(surface("water", TerrainType.WATERHOLE));

        descriptors.add(material("transition-land", null, LayerRole.TRANSITION,
            "terrain-kit/tv1/transition/land-edge.png", 128, 32, 6.0, 1.5,
            Anchor.BOUNDARY_TANGENT, PlacementPolicy.CONTOUR_BAND, true, 2));
        descriptors.add(material("transition-grass-bush", TerrainType.GRASSLAND,
            LayerRole.TRANSITION,
            "terrain-kit/tv1/transition/grass-bush.png", 128, 32, 6.0, 1.5,
            Anchor.BOUNDARY_TANGENT, PlacementPolicy.CONTOUR_BAND, true, 2));
        descriptors.add(material("shore-water", TerrainType.WATERHOLE,
            LayerRole.SHORELINE, "terrain-kit/tv1/shore/water-edge.png",
            128, 48, 6.0, 2.0, Anchor.BOUNDARY_TANGENT,
            PlacementPolicy.CONTOUR_BAND, true, 4));

        descriptors.add(material("decoration-grass-tuft", TerrainType.GRASSLAND,
            LayerRole.DECORATION, "terrain-kit/tv1/decor/grass-tuft.png",
            48, 48, 1.4, 1.4, Anchor.CELL_CENTER,
            PlacementPolicy.UNIFORM_DECORATION, true, 3));
        descriptors.add(material("decoration-bush", TerrainType.BUSH,
            LayerRole.DECORATION, "terrain-kit/tv1/decor/bush.png",
            64, 64, 1.8, 1.8, Anchor.CELL_CENTER,
            PlacementPolicy.UNIFORM_DECORATION, true, 5));
        descriptors.add(material("decoration-rock", TerrainType.DRY_SOIL,
            LayerRole.DECORATION, "terrain-kit/tv1/decor/rock.png",
            48, 40, 1.2, 1.0, Anchor.CELL_CENTER,
            PlacementPolicy.UNIFORM_DECORATION, true, 3));
        return new TerrainVisualKit(TV1_PROTOTYPE_VERSION, descriptors);
    }

    /**
     * The runtime TV2 kit.  Every descriptor names an in-JAR PNG and its
     * source-byte SHA-256; {@link TerrainVisualAssets} rejects any mismatch.
     */
    public static TerrainVisualKit tv2Packaged()
    {
        List<MaterialDescriptor> descriptors = new ArrayList<>();
        descriptors.add(packaged("surface-grassland", TerrainType.GRASSLAND,
            LayerRole.SURFACE, "surface/grassland.png", 64, 64, 6, 6,
            Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE, false, 0,
            "84d9a8d6470738467f50395d916b7660c6fb487dbc74c8c225495dcc98af7808"));
        descriptors.add(packaged("surface-open-plain", TerrainType.OPEN_PLAIN,
            LayerRole.SURFACE, "surface/open-plain.png", 64, 64, 6, 6,
            Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE, false, 0,
            "f811cc6d18365e6a742550f3ae7189a21a9fd6e5b0ad2a74b42f7a8e9aeb8c3e"));
        descriptors.add(packaged("surface-dry-soil", TerrainType.DRY_SOIL,
            LayerRole.SURFACE, "surface/dry-soil.png", 64, 64, 6, 6,
            Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE, false, 0,
            "c5dfa3295e9d8d80987c678d56d8330b7d98cf116a1dbf7dc87b1fe41a6c85fc"));
        descriptors.add(packaged("surface-bush", TerrainType.BUSH,
            LayerRole.SURFACE, "surface/bush.png", 64, 64, 6, 6,
            Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE, false, 0,
            "e0ead8634abc8986b5232096bf498aacf615b4e63abecf12432f05f549bb3740"));
        descriptors.add(packaged("surface-water", TerrainType.WATERHOLE,
            LayerRole.SURFACE, "surface/water.png", 64, 64, 6, 6,
            Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE, false, 0,
            "00274a115bae4f1be1c1c6f180e2b2d08b0444d1db1b3fc8956df6982410b71b"));
        descriptors.add(packaged("transition-land", null, LayerRole.TRANSITION,
            "transition/land-edge.png", 128, 32, 6, 1.5,
            Anchor.BOUNDARY_TANGENT, PlacementPolicy.CONTOUR_BAND, true, 2,
            "f14b4e66bd9e1f6ca27ef7407b0d9f9e7bbcb79b635803ec3159fb32ea7f9f37"));
        descriptors.add(packaged("transition-grass-bush", TerrainType.GRASSLAND,
            LayerRole.TRANSITION, "transition/grass-bush.png", 128, 32, 6, 1.5,
            Anchor.BOUNDARY_TANGENT, PlacementPolicy.CONTOUR_BAND, true, 2,
            "10f73d370043eafbdc244ba53cb69ad2b48ca81d81cfd76d6daad3dcba1ffa2d"));
        descriptors.add(packaged("shore-water", TerrainType.WATERHOLE,
            LayerRole.SHORELINE, "shore/water-edge.png", 128, 48, 6, 2,
            Anchor.BOUNDARY_TANGENT, PlacementPolicy.CONTOUR_BAND, true, 4,
            "28336831e8dea9e7ffc4f06f032ae8ba7df1d53f899049dfde7fb5b8186eb99a"));
        descriptors.add(packaged("decoration-grass-tuft", TerrainType.GRASSLAND,
            LayerRole.DECORATION, "decor/grass-tuft.png", 48, 48, 1.4, 1.4,
            Anchor.CELL_CENTER, PlacementPolicy.UNIFORM_DECORATION, true, 3,
            "49993360b8cc0b257101d2f3c553a0a9e37ef7512e4170b02e21043d2e76e25d"));
        descriptors.add(packaged("decoration-bush", TerrainType.BUSH,
            LayerRole.DECORATION, "decor/bush.png", 64, 64, 1.8, 1.8,
            Anchor.CELL_CENTER, PlacementPolicy.UNIFORM_DECORATION, true, 5,
            "af034379f622a801e0688033a49c0dd81183ebf46eeafe58fc860ae883b574a3"));
        descriptors.add(packaged("decoration-rock", TerrainType.DRY_SOIL,
            LayerRole.DECORATION, "decor/rock.png", 48, 48, 1.2, 1.0,
            Anchor.CELL_CENTER, PlacementPolicy.UNIFORM_DECORATION, true, 3,
            "2da2cc9d2537177a89329f3443e9e437e1a0441fa7ec403175510cf702bf809f"));
        return new TerrainVisualKit(TV2_PACKAGED_VERSION, descriptors);
    }

    public String getVersion() { return version; }

    public Map<String, MaterialDescriptor> getDescriptors()
    {
        return byId;
    }

    public MaterialDescriptor getSurface(TerrainType type)
    {
        return surfaceByTerrain.get(type);
    }

    public List<MaterialDescriptor> getByRole(LayerRole role)
    {
        List<MaterialDescriptor> result = new ArrayList<>();
        for(MaterialDescriptor descriptor : byId.values()) {
            if(descriptor.getRole() == role) result.add(descriptor);
        }
        return Collections.unmodifiableList(result);
    }

    private static MaterialDescriptor surface(String suffix, TerrainType type)
    {
        return material("surface-" + suffix, type, LayerRole.SURFACE,
            "terrain-kit/tv1/surface/" + suffix + ".png", 128, 128,
            6.0, 6.0, Anchor.WORLD_ORIGIN, PlacementPolicy.WORLD_TILE,
            false, 0);
    }

    private static MaterialDescriptor material(String id, TerrainType terrain,
                                               LayerRole role, String path,
                                               int pixelWidth, int pixelHeight,
                                               double worldWidth,
                                               double worldHeight, Anchor anchor,
                                               PlacementPolicy policy,
                                               boolean rotationAllowed,
                                               int transparentInset)
    {
        return new MaterialDescriptor(id, terrain, role, path, pixelWidth,
            pixelHeight, worldWidth, worldHeight, anchor, policy,
            rotationAllowed, transparentInset, null);
    }

    private static MaterialDescriptor packaged(String id, TerrainType terrain,
                                               LayerRole role, String suffix,
                                               int pixelWidth, int pixelHeight,
                                               double worldWidth, double worldHeight,
                                               Anchor anchor, PlacementPolicy policy,
                                               boolean rotationAllowed,
                                               int transparentInset, String sha256)
    {
        return new MaterialDescriptor(id, terrain, role,
            "terrain-kit/tv2/" + suffix, pixelWidth, pixelHeight, worldWidth,
            worldHeight, anchor, policy, rotationAllowed, transparentInset,
            requireText(sha256, "resourceSha256"));
    }

    private static String requireText(String value, String name)
    {
        if(value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return value;
    }

    private static int positive(int value, String name)
    {
        if(value <= 0) throw new IllegalArgumentException(name + " must be positive.");
        return value;
    }
}
