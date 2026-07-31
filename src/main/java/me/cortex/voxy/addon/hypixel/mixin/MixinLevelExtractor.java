package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelExtractor.class, priority = 2000)
public class MixinLevelExtractor {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void voxy_hypixel_addon$beginLevelTransition(ClientLevel level, CallbackInfo ci) {
        HypixelManager.beginLevelTransition();
    }
}
