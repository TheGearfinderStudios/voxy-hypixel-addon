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
    }

    private static ConfigData data = new ConfigData();

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                try (var reader = Files.newBufferedReader(CONFIG_PATH)) {
                    data = GSON.fromJson(reader, ConfigData.class);
                    if (data == null) {
                        data = new ConfigData();
                    }
                }
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
}
