package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import net.minecraft.client.multiplayer.ClientLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// targets= (string) defers resolution until the class loads - value= (class ref) fails on early main-thread validation
@Mixin(targets = "net.minecraft.client.renderer.extract.LevelExtractor", remap = false, priority = 2000)
public class MixinLevelExtractor {
    // remap=false + explicit descriptor required: no refmap generated without mappings (MC 26.2 ships native Mojmap names)
    @Inject(method = "setLevel(Lnet/minecraft/client/multiplayer/ClientLevel;)V", at = @At("HEAD"), remap = false)
    private void voxy_hypixel_addon$beginLevelTransition(ClientLevel level, CallbackInfo ci) {
        HypixelManager.beginLevelTransition();
    }
}
