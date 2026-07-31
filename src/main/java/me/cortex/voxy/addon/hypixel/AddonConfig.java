package me.cortex.voxy.addon.hypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class AddonConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxy-hypixel-addon.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class BoundingBox {
        public int minX;
        public int minZ;
        public int maxX;
        public int maxZ;

        public BoundingBox() {}
        public BoundingBox(int minX, int minZ, int maxX, int maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean intersects(int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
            return chunkMinX <= maxX && chunkMaxX >= minX &&
                   chunkMinZ <= maxZ && chunkMaxZ >= minZ;
        }
    }

    public static class AreaMapping {
        public String targetArea;
        // If present, merged maps can only write to this section of the Voxy cache
        public java.util.List<BoundingBox> allowedBoxes = new java.util.ArrayList<>();
        // If specified, forces cross-dimension cache merging (e.g. "OVERWORLD", "NETHER", "END")
        public String targetDimension;

        public AreaMapping() {}
        public AreaMapping(String targetArea) {
            this.targetArea = targetArea;
        }
        public AreaMapping(String targetArea, java.util.List<BoundingBox> allowedBoxes) {
            this.targetArea = targetArea;
            this.allowedBoxes = allowedBoxes;
        }
        public AreaMapping(String targetArea, String targetDimension) {
            this.targetArea = targetArea;
            this.targetDimension = targetDimension;
        }
    }

    public static class ConfigData {
        public Boolean fastReloads = true;
        public Boolean skipFakeReloads = true;
        // If enabled: Hypixel Alpha uses the same Voxy cache as the main server (saves disk space).
        // If disabled: Hypixel Alpha gets a dedicated cache folder (prevents cache bleed if Alpha has unreleased terrain changes).
        public Boolean mergeAlphaHypixel = true;
        public Boolean enableAreaMerging = true;
        public java.util.Map<String, AreaMapping> areaMappings = new java.util.HashMap<>();

        public ConfigData() {
            // Park Hub clone lacks some structures, restrict writes to the Park islands and Jungle area to avoid overlapping End
            java.util.List<BoundingBox> parkBoxes = new java.util.ArrayList<>();
            parkBoxes.add(new BoundingBox(-470, -25, -255, 100)); // Park main body
            parkBoxes.add(new BoundingBox(-500, -130, -400, -15)); // Jungle area
            areaMappings.put("SKYBLOCK_foraging_1", new AreaMapping("SKYBLOCK_hub", parkBoxes)); // park
            
            // Galatea Hub clone lacks Savanna, restrict cache writes to the Galatea island boundaries to protect Savanna cache
            java.util.List<BoundingBox> galateaBoxes = new java.util.ArrayList<>();
            galateaBoxes.add(new BoundingBox(-770, -110, -520, 110));
            areaMappings.put("SKYBLOCK_foraging_2", new AreaMapping("SKYBLOCK_hub", galateaBoxes)); // galatea
            
            areaMappings.put("SKYBLOCK_combat_1", new AreaMapping("SKYBLOCK_hub")); // spider
            
            // End Hub clone lacks other parts of the hub, restrict cache writes to the End island boundaries
            java.util.List<BoundingBox> endBoxes = new java.util.ArrayList<>();
            endBoxes.add(new BoundingBox(-800, -420, -440, -120));
            areaMappings.put("SKYBLOCK_combat_3", new AreaMapping("SKYBLOCK_hub", endBoxes)); // end
            
            areaMappings.put("SKYBLOCK_crimson_isle", new AreaMapping("SKYBLOCK_hub", "OVERWORLD")); // crimson
            areaMappings.put("SKYBLOCK_mining_1", new AreaMapping("SKYBLOCK_hub")); // gold mine
            areaMappings.put("SKYBLOCK_farming_1", new AreaMapping("SKYBLOCK_hub")); // barn
            
            //areaMappings.put("SKYBLOCK_mining_2", new AreaMapping("SKYBLOCK_hub")); // CAN'T MERGE, Hypixel made it not fit the main hub
        }
    }

    private static ConfigData data = new ConfigData();

    public static String getCanonicalAreaId(String areaId) {
        if (areaId == null) return null;
        if (data.enableAreaMerging == null || !data.enableAreaMerging) return areaId;
        AreaMapping mapped = data.areaMappings.get(areaId);
        return (mapped != null && mapped.targetArea != null && !mapped.targetArea.isEmpty()) ? mapped.targetArea : areaId;
    }

    public static String getTargetDimension(String areaId) {
        if (areaId == null) return null;
        if (data.enableAreaMerging == null || !data.enableAreaMerging) return null;
        AreaMapping mapped = data.areaMappings.get(areaId);
        return mapped != null ? mapped.targetDimension : null;
    }

    public static boolean isIngestAllowed(String areaId, int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
        if (areaId == null) return true;
        if (data.enableAreaMerging == null || !data.enableAreaMerging) return true;
        AreaMapping mapped = data.areaMappings.get(areaId);
        if (mapped == null || mapped.allowedBoxes == null || mapped.allowedBoxes.isEmpty()) {
            return true;
        }
        for (BoundingBox box : mapped.allowedBoxes) {
            if (box.intersects(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
                return true;
            }
        }
        return false;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                    if (loaded != null) {
                        if (loaded.fastReloads != null) data.fastReloads = loaded.fastReloads;
                        if (loaded.skipFakeReloads != null) data.skipFakeReloads = loaded.skipFakeReloads;
                        if (loaded.mergeAlphaHypixel != null) data.mergeAlphaHypixel = loaded.mergeAlphaHypixel;
                        if (loaded.enableAreaMerging != null) data.enableAreaMerging = loaded.enableAreaMerging;
                        if (loaded.areaMappings != null) {
                            data.areaMappings.putAll(loaded.areaMappings);
                        }
                    }
                }
                save();
            } else {
                save();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (var writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isFastReloads() {
        return data.fastReloads;
    }

    public static void setFastReloads(boolean value) {
        data.fastReloads = value;
        save();
    }

    public static boolean isSkipFakeReloads() {
        return data.skipFakeReloads;
    }

    public static void setSkipFakeReloads(boolean value) {
        data.skipFakeReloads = value;
        save();
    }

    public static boolean isMergeAlphaHypixel() {
        return data.mergeAlphaHypixel;
    }

    public static void setMergeAlphaHypixel(boolean value) {
        data.mergeAlphaHypixel = value;
        save();
    }

    public static boolean isEnableAreaMerging() {
        return data.enableAreaMerging;
    }

    public static void setEnableAreaMerging(boolean value) {
        data.enableAreaMerging = value;
        save();
    }
}
