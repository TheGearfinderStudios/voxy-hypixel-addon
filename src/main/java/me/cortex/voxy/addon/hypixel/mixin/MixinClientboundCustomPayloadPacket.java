package me.cortex.voxy.addon.hypixel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.cortex.voxy.addon.hypixel.network.HypixelPacketInterceptor;
import me.cortex.voxy.addon.hypixel.network.HypixelPacketInterceptor.DualPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts the S2C custom payload codec at the lowest possible level so we
 * decode our Hypixel packets independently of PayloadTypeRegistry registration order.
 *
 * Priority 889 matches HM-API's priority (888) + 1 so we wrap after them if both present,
 * meaning both get their decoded payloads correctly.
 *
 * Technique adapted from HM-API by AzureAaron (Apache-2.0).
 * https://github.com/AzureAaron/hm-api
 */
@Mixin(value = ClientboundCustomPayloadPacket.class, priority = 889)
public abstract class MixinClientboundCustomPayloadPacket {
    @Shadow public abstract CustomPacketPayload payload();

    /** Wrap the vanilla S2C codec with ours during class initialization. */
    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;"),
            remap = false
    )
    private static StreamCodec<FriendlyByteBuf, CustomPacketPayload> wrapS2CCodec(
            StreamCodec<FriendlyByteBuf, CustomPacketPayload> original) {
        return HypixelPacketInterceptor.wrap(original);
    }

    /** When a DualPayload arrives, dispatch the original then ours. */
    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(ClientCommonPacketListener listener, CallbackInfo ci) {
        if (payload() instanceof DualPayload dual) {
            new ClientboundCustomPayloadPacket(dual.original()).handle(listener);
            new ClientboundCustomPayloadPacket(dual.ours()).handle(listener);
            ci.cancel();
        }
    }
}
