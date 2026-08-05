package me.cortex.voxy.addon.hypixel.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps the vanilla S2C custom payload StreamCodec so our Hypixel packets are
 * decoded from a buffer copy in parallel, regardless of which mod registered
 * the PayloadTypeRegistry entries first (first-come-first-served problem).
 *
 * Technique adapted from HM-API by AzureAaron (Apache-2.0).
 * https://github.com/AzureAaron/hm-api
 */
public final class HypixelPacketInterceptor {
    private HypixelPacketInterceptor() {}

    /**
     * Carrier holding both the vanilla-decoded payload AND our decoded payload.
     * The mixin on ClientboundCustomPayloadPacket#handle dispatches both in sequence.
     */
    public record DualPayload(CustomPacketPayload original, CustomPacketPayload ours)
            implements CustomPacketPayload {
        private static final Type<DualPayload> ID =
                new Type<>(Identifier.fromNamespaceAndPath("voxy-hypixel-addon", "dual"));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    // Map of identifier -> our codec, for the two S2C packets we care about
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final Map<Identifier, StreamCodec<FriendlyByteBuf, CustomPacketPayload>> OUR_CODECS;
    static {
        OUR_CODECS = new HashMap<>();
        OUR_CODECS.put(HypixelPayloads.HelloS2CPacket.ID.id(),
                (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) (StreamCodec) HypixelPayloads.HelloS2CPacket.PACKET_CODEC);
        OUR_CODECS.put(HypixelPayloads.LocationUpdateS2CPacket.ID.id(),
                (StreamCodec<FriendlyByteBuf, CustomPacketPayload>) (StreamCodec) HypixelPayloads.LocationUpdateS2CPacket.PACKET_CODEC);
    }

    /**
     * Called from the mixin on ClientboundCustomPayloadPacket's static initializer.
     * Wraps the vanilla S2C codec to intercept our target identifiers.
     */
    public static StreamCodec<FriendlyByteBuf, CustomPacketPayload> wrap(
            StreamCodec<FriendlyByteBuf, CustomPacketPayload> original) {
        return new StreamCodec<>() {
            @Override
            @SuppressWarnings("unchecked")
            public CustomPacketPayload decode(FriendlyByteBuf buf) {
                // Peek the identifier without consuming it (slice = shared, same readerIndex)
                FriendlyByteBuf peek = new FriendlyByteBuf(buf.slice());
                Identifier id = Identifier.STREAM_CODEC.decode(peek);

                StreamCodec<FriendlyByteBuf, ? extends CustomPacketPayload> ourCodec = OUR_CODECS.get(id);

                // Let vanilla decode normally (consumes buf)
                CustomPacketPayload orig = original.decode(buf);

                if (ourCodec == null) return orig; // packet we don't care about

                // Decode our version from a fresh copy starting after the identifier
                FriendlyByteBuf ourBuf = new FriendlyByteBuf(peek.slice()); // peek's readerIndex is after the id
                CustomPacketPayload ours = ((StreamCodec<FriendlyByteBuf, CustomPacketPayload>) ourCodec).decode(ourBuf);

                return new DualPayload(orig, ours);
            }

            @Override
            public void encode(FriendlyByteBuf buf, CustomPacketPayload payload) {
                original.encode(buf, payload);
            }
        };
    }
}
