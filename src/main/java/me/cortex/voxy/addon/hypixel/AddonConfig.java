package me.cortex.voxy.addon.hypixel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public class AddonConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxy-hypixel-addon.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class ConfigData {
        public boolean fastReloads = true;
        public boolean skipFakeReloads = true;
        // If enabled: Hypixel Alpha uses the same Voxy cache as the main server (saves disk space).
        // If disabled: Hypixel Alpha gets a dedicated cache folder (prevents cache bleed if Alpha has unreleased terrain changes).
        public boolean mergeAlphaHypixel = true;
        public java.util.Map<String, String> areaMappings = new java.util.HashMap<>();

        public ConfigData() {
            // SkyBlock Hub: Group all maps with the same buildings and coord space
            areaMappings.put("SKYBLOCK_foraging_1", "SKYBLOCK_hub"); // park
            areaMappings.put("SKYBLOCK_foraging_2", "SKYBLOCK_hub"); // galatea
            areaMappings.put("SKYBLOCK_combat_1", "SKYBLOCK_hub"); // spider
            areaMappings.put("SKYBLOCK_combat_3", "SKYBLOCK_hub"); // end
            areaMappings.put("SKYBLOCK_crimson_isle", "SKYBLOCK_hub"); // crimson
            areaMappings.put("SKYBLOCK_mining_1", "SKYBLOCK_hub"); // gold mine
            areaMappings.put("SKYBLOCK_farming_1", "SKYBLOCK_hub"); // barn
            
            //areaMappings.put("SKYBLOCK_mining_2", "SKYBLOCK_hub"); // CAN'T MERGE, Hypixel made it not fit the main hub
        }
    }

    private static ConfigData data = new ConfigData();

    public static String getCanonicalAreaId(String areaId) {
        if (areaId == null) return null;
        String mapped = data.areaMappings.get(areaId);
        return (mapped != null && !mapped.isEmpty()) ? mapped : areaId;
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    ConfigData loaded = GSON.fromJson(reader, ConfigData.class);
                    if (loaded != null) {
                        data.fastReloads = loaded.fastReloads;
                        data.skipFakeReloads = loaded.skipFakeReloads;
                        data.mergeAlphaHypixel = loaded.mergeAlphaHypixel;
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
}
