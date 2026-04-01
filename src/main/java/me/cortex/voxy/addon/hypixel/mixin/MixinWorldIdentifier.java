package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Mixin(value = WorldIdentifier.class, remap = false)
public class MixinWorldIdentifier {
    @Unique
    public String voxy_hypixel_addon$subId = null;

    @Shadow @Final public long biomeSeed;
    @Shadow @Final public ResourceKey<Level> key;

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void onHashCode(CallbackInfoReturnable<Integer> cir) {
        // We could implement custom hashcode here if needed
        // But for now, let's keep it simple.
    }

    @Inject(method = "getWorldId", at = @At("HEAD"), cancellable = true)
    private static void onGetWorldId(WorldIdentifier identifier, CallbackInfoReturnable<String> cir) {
        String subId = ((MixinWorldIdentifier)(Object)identifier).voxy_hypixel_addon$subId;
        if (subId != null) {
            String data = identifier.biomeSeed + identifier.key.toString() + subId;
            try {
                cir.setReturnValue(bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Unique
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
