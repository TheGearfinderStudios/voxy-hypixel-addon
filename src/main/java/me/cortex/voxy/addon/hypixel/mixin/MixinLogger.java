package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.common.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Logger.class, remap = false)
public abstract class MixinLogger {
    @Inject(method = "error", at = @At("HEAD"), cancellable = true)
    private static void onLoggerError(Object[] args, CallbackInfo ci) {
        if (args != null && args.length > 0 && "Null world selected".equals(args[0]) && HypixelManager.isHypixel()) {
            ci.cancel();
        }
    }
}
