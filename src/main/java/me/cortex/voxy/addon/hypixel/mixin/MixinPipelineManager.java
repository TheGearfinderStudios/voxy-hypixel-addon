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
        if (HypixelManager.isHypixel() && AddonConfig.isSkipFakeReloads()) {
            // Only cancel the destruction if it was triggered by Minecraft's level/dimension transition
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName();
                String methodName = element.getMethodName();
                if (className.contains("MixinMinecraft_PipelineManagement") || methodName.contains("updateLevelInEngines")) {
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
