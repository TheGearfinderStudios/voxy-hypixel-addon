package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.AddonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Locale;

// targets= (string) defers resolution until Voxy's class is loaded - value= (class ref) fails early Mixin validation
@Mixin(targets = "me.cortex.voxy.client.VoxyClientInstance", remap = false)
public class MixinVoxyClientInstance {
    // Canonicalize any *.hypixel.net storage path to a shared "mc.hypixel.net" folder so all
    // Hypixel subdomains (mc.hypixel.net, lobby.hypixel.net, etc.) reuse the same cache.
    @Inject(method = "getBasePath", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void voxy_hypixel_addon$canonicalizeHypixelPath(CallbackInfoReturnable<Path> cir) {
        Path original = cir.getReturnValue();
        if (original == null) return;

        Path filenamePath = original.getFileName();
        if (filenamePath == null) return;

        String host = filenamePath.toString().toLowerCase(Locale.ROOT);

        // Voxy may append _25565 (default port) to the folder name - strip it
        if (host.endsWith("_25565")) {
            host = host.substring(0, host.length() - "_25565".length());
        }

        // Only act on hypixel.net and its subdomains
        if (!host.equals("hypixel.net") && !host.endsWith(".hypixel.net")) return;

        // Optionally keep alpha separate (config off = alpha gets its own folder)
        if (host.equals("alpha.hypixel.net") && !AddonConfig.isMergeAlphaHypixel()) return;

        Path savesRoot = original.getParent();
        if (savesRoot != null) {
            cir.setReturnValue(savesRoot.resolve("mc.hypixel.net"));
        }
    }
}