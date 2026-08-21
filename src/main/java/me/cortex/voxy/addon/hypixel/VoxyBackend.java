package me.cortex.voxy.addon.hypixel;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handles Voxy-specific LOD reload on area change. Isolated so it's only classloaded when Voxy is present. */
public class VoxyBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger("voxy-hypixel-addon/voxy");
    private static boolean warnedInterfaceMissing = false;

    private static void sendChatError(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[Voxy & DH Addon] " + message)
                        .withStyle(s -> s.withColor(0xFF5555)));
                }
            });
        }
    }

    public static void onAreaChange(String serverType, String rawArea, String canonicalArea) {
        long startTime = System.currentTimeMillis();
        var lr = Minecraft.getInstance().levelRenderer;
        if (lr instanceof IVoxyRenderSystemHolder getter) {
            try {
                getter.voxy$shutdownRenderer();
                long shutdownTime = System.currentTimeMillis();

                getter.voxy$setWorld(Minecraft.getInstance().level);
                getter.voxy$createRenderer();
                long createTime = System.currentTimeMillis();

                LOGGER.info("[Voxy-Addon-Benchmark] Reload in {}ms (Shutdown: {}ms, Create: {}ms). Fast Reloads = {}",
                    createTime - startTime, shutdownTime - startTime, createTime - shutdownTime,
                    AddonConfig.isFastReloads());
            } catch (Throwable t) {
                LOGGER.error("[Voxy-Addon] Voxy renderer reload failed", t);
                sendChatError("Voxy reload failed: " + t.getClass().getSimpleName() + " (check logs)");
            }
        } else {
            LOGGER.error("[Voxy-Addon] LevelRenderer does not implement IVoxyRenderSystemHolder (incompatible Voxy version?)");
            if (!warnedInterfaceMissing) {
                warnedInterfaceMissing = true;
                sendChatError("Voxy integration failed: LevelRenderer missing Voxy hooks (incompatible Voxy version?)");
            }
        }
    }
}
