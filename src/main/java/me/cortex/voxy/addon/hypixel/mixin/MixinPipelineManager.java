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
            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
                java.lang.reflect.Method getCurrentDimensionMethod = irisClass.getMethod("getCurrentDimension");
                java.lang.reflect.Field lastDimensionField = irisClass.getField("lastDimension");

                Object current = getCurrentDimensionMethod.invoke(null);
                Object last = lastDimensionField.get(null);

                if (current != null && last != null && !current.equals(last)) {
                    // Bypass destroying the pipeline during dimension change on Hypixel!
                    ci.cancel();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
