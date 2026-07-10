package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "updateLevelInEngines", at = @At("HEAD"))
    private void voxy$onUpdateLevelInEnginesStart(ClientLevel level, CallbackInfo ci) {
        HypixelManager.isDimensionChanging = true;
    }

    @Inject(method = "updateLevelInEngines", at = @At("RETURN"))
    private void voxy$onUpdateLevelInEnginesEnd(ClientLevel level, CallbackInfo ci) {
        HypixelManager.isDimensionChanging = false;
    }
}
