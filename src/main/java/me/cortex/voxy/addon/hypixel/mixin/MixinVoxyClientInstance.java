package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.client.VoxyClientInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Locale;

@Mixin(value = VoxyClientInstance.class, remap = false)
public class MixinVoxyClientInstance {
    @Inject(method = "getBasePath", at = @At("RETURN"), cancellable = true)
    private static void voxy_hypixel_addon$canonicalizeStoragePath(
            CallbackInfoReturnable<Path> cir) {
        Path original = cir.getReturnValue();
        Path storageNamePath = original.getFileName();
        if (storageNamePath == null) {
            return;
        }

        //Voxy has already resolved and sanitized the connection address here,
        //even during early join setup when Minecraft#getCurrentServer is still
        //null. Strip its optional default-port suffix before matching.
        String host = storageNamePath.toString().toLowerCase(Locale.ROOT);
        if (host.endsWith("_25565")) {
            host = host.substring(0, host.length() - "_25565".length());
        }
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) {
            return;
        }

        Path savesRoot = original.getParent();
        if (savesRoot != null) {
            cir.setReturnValue(savesRoot.resolve("hypixel.net"));
        }
    }
}
