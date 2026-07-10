package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.addon.hypixel.AddonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public class MixinPipelineManager {

    @Inject(method = "destroyPipeline", at = @At("HEAD"), cancellable = true)
    private void onDestroyPipeline(CallbackInfo ci) {
        if (HypixelManager.isHypixel() && AddonConfig.isSkipFakeReloads() && HypixelManager.isDimensionChanging) {
            // Bypass destroying the pipeline during dimension change on Hypixel!
            ci.cancel();
        }
    }
}
