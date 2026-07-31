package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// MC 26.1.x only: setLevel lived on LevelRenderer before it was moved to LevelExtractor in 26.2.
// On 26.2 this is a silent no-op (targets= string form + setLevel no longer exists on LevelRenderer).
//
// UPGRADE TO 26.2: this mixin is replaced by MixinLevelExtractor — no changes needed here.
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer", remap = false, priority = 2000)
public class MixinLevelRenderer {
    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"), remap = false, require = 0)
    private void voxy_hypixel_addon$beginLevelTransition(ClientLevel level, CallbackInfo ci) {
        HypixelManager.beginLevelTransition();
    }
}
