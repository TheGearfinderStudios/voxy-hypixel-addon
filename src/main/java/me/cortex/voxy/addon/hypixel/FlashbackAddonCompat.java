package me.cortex.voxy.addon.hypixel;

import me.cortex.voxy.addon.hypixel.access.IFlashbackHypixelMeta;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;

public class FlashbackAddonCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("voxy-hypixel-addon/flashback");
    public static final boolean FLASHBACK_INSTALLED = FabricLoader.getInstance().isModLoaded("flashback");
    private static final boolean VOXY_INSTALLED = FabricLoader.getInstance().isModLoaded("voxy");

    private static Method getReplayServerMethod;
    private static Method getMetadataMethod;
    private static Method getVoxyReplayStoragePathMethod;
    private static boolean initialized = false;

    private static void initReflection() {
        if (initialized) return;
        initialized = true;
        if (!FLASHBACK_INSTALLED) return;
        try {
            Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
            getReplayServerMethod = flashbackClass.getMethod("getReplayServer");
            Class<?> replayServerClass = Class.forName("com.moulberry.flashback.playback.ReplayServer");
            getMetadataMethod = replayServerClass.getMethod("getMetadata");
        } catch (Throwable t) {
            LOGGER.warn("[Voxy-Addon-Flashback] Could not initialize Flashback reflection methods: " + t.getMessage());
        }

        if (VOXY_INSTALLED) {
            try {
                Class<?> voxyFlashbackCompat = Class.forName("me.cortex.voxy.client.compat.FlashbackCompat");
                getVoxyReplayStoragePathMethod = voxyFlashbackCompat.getMethod("getReplayStoragePath");
            } catch (Throwable ignored) {}
        }
    }

    public static Object getActiveReplayMetadata() {
        if (!FLASHBACK_INSTALLED) return null;
        initReflection();
        if (getReplayServerMethod == null || getMetadataMethod == null) return null;
        try {
            Object replayServer = getReplayServerMethod.invoke(null);
            if (replayServer != null) {
                return getMetadataMethod.invoke(replayServer);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isHypixelReplay() {
        if (!FLASHBACK_INSTALLED) return false;
        initReflection();
        if (getVoxyReplayStoragePathMethod != null) {
            try {
                Path path = (Path) getVoxyReplayStoragePathMethod.invoke(null);
                if (path != null && path.toString().toLowerCase().contains("hypixel.net")) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        Object meta = getActiveReplayMetadata();
        if (meta instanceof IFlashbackHypixelMeta hypixelMeta && hypixelMeta.voxy_hypixel_addon$getHypixelArea() != null) {
            return true;
        }
        return false;
    }

    public static String getReplayHypixelArea() {
        Object meta = getActiveReplayMetadata();
        if (meta instanceof IFlashbackHypixelMeta hypixelMeta) {
            return hypixelMeta.voxy_hypixel_addon$getHypixelArea();
        }
        return null;
    }

    public static String getReplayHypixelRawArea() {
        Object meta = getActiveReplayMetadata();
        if (meta instanceof IFlashbackHypixelMeta hypixelMeta) {
            return hypixelMeta.voxy_hypixel_addon$getHypixelRawArea();
        }
        return null;
    }
}
