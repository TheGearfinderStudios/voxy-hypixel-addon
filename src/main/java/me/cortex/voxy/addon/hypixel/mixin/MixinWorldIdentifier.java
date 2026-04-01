package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.access.IPerAreaWorldIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Mixin(value = WorldIdentifier.class, remap = false)
public abstract class MixinWorldIdentifier implements IPerAreaWorldIdentifier {
    @Shadow public long biomeSeed;
    @Shadow public ResourceKey<Level> key;

    @Unique
    private String voxy_hypixel_addon$subId = "";

    @Override
    @Unique
    public void setSubId(String subId) {
        this.voxy_hypixel_addon$subId = subId;
    }

    @Inject(method = "getWorldId()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private void onGetWorldId(CallbackInfoReturnable<String> cir) {
        if (this.voxy_hypixel_addon$subId != null && !this.voxy_hypixel_addon$subId.isEmpty()) {
            String data = this.biomeSeed + this.key.toString() + this.voxy_hypixel_addon$subId;
            try {
                cir.setReturnValue(bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))).substring(0, 32));
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
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
