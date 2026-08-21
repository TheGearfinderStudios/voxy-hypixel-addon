package me.cortex.voxy.addon.hypixel.mixin.vanilla;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import net.minecraft.client.multiplayer.ClientLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// MC 26.2+ only: LevelExtractor is a new class that didn't exist in 26.1.x.
// This mixin silently skips on 26.1.x (targets= string form, so no crash, just no-op).
//
// BACKPORT TO 26.1.x: change targets= to "net.minecraft.client.renderer.LevelRenderer"
// The method signature (setLevel(ClientLevel)V) is identical --- that's the only change needed.
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor", remap = false, priority = 2000)
public class MixinLevelExtractor {
    // remap=false + explicit descriptor required: no refmap generated without mappings (MC 26.2 ships native Mojmap names)
    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"), remap = false, require = 0)
    private void voxy_hypixel_addon$beginLevelTransition(ClientLevel level, CallbackInfo ci) {
        HypixelManager.beginLevelTransition();
    }
}

