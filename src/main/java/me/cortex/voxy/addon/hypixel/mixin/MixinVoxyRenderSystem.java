package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.addon.hypixel.AddonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public class MixinVoxyRenderSystem {

    @Redirect(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/lang/System;gc()V"),
        require = 0
    )
    private void voxy$redirectGc() {
        if (!AddonConfig.isFastReloads()) {
            System.gc();
        }
    }

    @Redirect(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11C;glFinish()V"),
        require = 0
    )
    private void voxy$redirectGlFinishC() {
        if (!AddonConfig.isFastReloads()) {
            org.lwjgl.opengl.GL11C.glFinish();
        }
    }

    @Redirect(
        method = "<init>",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glFinish()V"),
        require = 0
    )
    private void voxy$redirectGlFinish() {
        if (!AddonConfig.isFastReloads()) {
            org.lwjgl.opengl.GL11.glFinish();
        }
    }
}
