package me.cortex.voxy.addon.hypixel;

import me.cortex.voxy.addon.hypixel.access.IFlashbackHypixelMeta;
import me.cortex.voxy.client.compat.FlashbackCompat;
import me.cortex.voxy.common.Logger;

import java.lang.reflect.Method;
import java.nio.file.Path;

public class FlashbackAddonCompat {
    private static Method getReplayServerMethod;
    private static Method getMetadataMethod;
    private static boolean initialized = false;

    private static void initReflection() {
        if (initialized) return;
        initialized = true;
        if (!FlashbackCompat.FLASHBACK_INSTALLED) return;
        try {
            Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
            getReplayServerMethod = flashbackClass.getMethod("getReplayServer");
            Class<?> replayServerClass = Class.forName("com.moulberry.flashback.playback.ReplayServer");
            getMetadataMethod = replayServerClass.getMethod("getMetadata");
        } catch (Throwable t) {
            Logger.warn("[Voxy-Addon-Flashback] Could not initialize Flashback reflection methods: " + t.getMessage());
        }
    }

    public static Object getActiveReplayMetadata() {
        if (!FlashbackCompat.FLASHBACK_INSTALLED) return null;
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
        if (!FlashbackCompat.FLASHBACK_INSTALLED) return false;
        Path path = FlashbackCompat.getReplayStoragePath();
        if (path != null) {
            String pathStr = path.toString().toLowerCase();
            if (pathStr.contains("hypixel.net")) {
                return true;
            }
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
