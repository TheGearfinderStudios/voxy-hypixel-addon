package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.common.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.cortex.voxy.client.mixin.minecraft.MixinLevelRenderer", remap = false)
public class MixinLevelRenderer {
    @Redirect(method = "createRenderer", at = @At(value = "INVOKE", target = "Lme/cortex/voxy/common/Logger;error(Ljava/lang/String;)V"))
    private void onLoggerError(String message) {
        if (HypixelManager.isHypixel()) {
            // Silently ignore during hypixel gating
            return;
        }
        Logger.error(message);
    }
}
