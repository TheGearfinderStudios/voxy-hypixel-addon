package me.cortex.voxy.addon.hypixel.mixin.dh;

import me.cortex.voxy.addon.hypixel.AddonConfig;
import me.cortex.voxy.addon.hypixel.HypixelManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.SharedApi", remap = false)
public class MixinDhSharedApi {
    private static Method getChunkPosMethod = null;
    private static Method getXMethod = null;
    private static Method getZMethod = null;
    private static boolean reflectionInitialized = false;

    private static void initReflection(Class<?> chunkWrapperClass) {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            getChunkPosMethod = chunkWrapperClass.getMethod("getChunkPos");
            Class<?> dhChunkPosClass = Class.forName("com.seibel.distanthorizons.core.pos.DhChunkPos");
            getXMethod = dhChunkPosClass.getMethod("getX");
            getZMethod = dhChunkPosClass.getMethod("getZ");
        } catch (Throwable ignored) {}
    }

    @Inject(method = "applyChunkUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void voxy_hypixel_addon$shouldIngestDhChunk(@Coerce Object chunkWrapper, @Coerce Object levelWrapper, boolean waitForLoadedWorld, CallbackInfo ci) {
        if (HypixelManager.isHypixel() && chunkWrapper != null) {
            try {
                if (!reflectionInitialized) {
                    initReflection(chunkWrapper.getClass());
                }
                if (getChunkPosMethod != null && getXMethod != null && getZMethod != null) {
                    Object chunkPos = getChunkPosMethod.invoke(chunkWrapper);
                    if (chunkPos != null) {
                        int cx = (int) getXMethod.invoke(chunkPos);
                        int cz = (int) getZMethod.invoke(chunkPos);
                        int minX = cx << 4;
                        int minZ = cz << 4;
                        int maxX = minX + 15;
                        int maxZ = minZ + 15;

                        if (!AddonConfig.isIngestAllowed(HypixelManager.getRawAreaId(), minX, minZ, maxX, maxZ)) {
                            ci.cancel();
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
}
