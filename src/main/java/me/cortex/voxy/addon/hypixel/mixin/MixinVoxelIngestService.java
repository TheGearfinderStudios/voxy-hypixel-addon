package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.addon.hypixel.AddonConfig;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.cortex.voxy.common.world.service.VoxelIngestService", remap = false)
public class MixinVoxelIngestService {
    @Inject(method = "shouldIngestSection", at = @At("HEAD"), cancellable = true)
    private static void voxy_hypixel_addon$shouldIngest(LevelChunkSection section, int cx, int cy, int cz, CallbackInfoReturnable<Boolean> cir) {
        if (HypixelManager.isHypixel()) {
            int minX = cx << 4;
            int minZ = cz << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            if (!AddonConfig.isIngestAllowed(HypixelManager.getRawAreaId(), minX, minZ, maxX, maxZ)) {
                cir.setReturnValue(false);
            }
        }
    }
}
