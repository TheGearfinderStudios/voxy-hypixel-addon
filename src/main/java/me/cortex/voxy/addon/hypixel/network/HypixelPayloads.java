package me.cortex.voxy.addon.hypixel.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class HypixelPayloads {
    private HypixelPayloads() {}

    /**
     * Handshake packet sent by Hypixel upon connecting ("hypixel:hello")
     */
    public record HelloS2CPacket(boolean success, int environmentOrdinal) implements CustomPacketPayload {
        public static final Type<HelloS2CPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("hypixel", "hello"));
        
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloS2CPacket> PACKET_CODEC = new StreamCodec<>() {
            @Override
            public HelloS2CPacket decode(RegistryFriendlyByteBuf buf) {
                try {
                    boolean success = buf.readBoolean();
                    int env = success ? buf.readVarInt() : -1;
                    return new HelloS2CPacket(success, env);
                } catch (Throwable t) {
                    return new HelloS2CPacket(false, -1);
                } finally {
                    buf.readerIndex(buf.writerIndex()); // Consume all bytes to prevent Netty decoder overflow exception
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, HelloS2CPacket value) {
                throw new UnsupportedOperationException("S2C packet encode not supported");
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Event registration packet sent to Hypixel ("hypixel:register")
     */
    public record RegisterC2SPacket(int version, Map<Identifier, Integer> eventsToRegister) implements CustomPacketPayload {
        public static final Type<RegisterC2SPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("hypixel", "register"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RegisterC2SPacket> PACKET_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RegisterC2SPacket::version,
                ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT, 5), RegisterC2SPacket::eventsToRegister,
                RegisterC2SPacket::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * Location update event packet sent by Hypixel ("hyevent:location")
     */
    public record LocationUpdateS2CPacket(
            boolean success,
            int packetVersion,
            String serverName,
            Optional<String> serverType,
            Optional<String> lobbyName,
            Optional<String> mode,
            Optional<String> map
    ) implements CustomPacketPayload {
        public static final Type<LocationUpdateS2CPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("hyevent", "location"));
        
        public static final StreamCodec<RegistryFriendlyByteBuf, LocationUpdateS2CPacket> PACKET_CODEC = new StreamCodec<>() {
            @Override
            public LocationUpdateS2CPacket decode(RegistryFriendlyByteBuf buf) {
                try {
                    boolean success = buf.readBoolean();
                    if (!success) {
                        return new LocationUpdateS2CPacket(false, -1, "", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                    }
                    int ver = buf.readVarInt();
                    String serverName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    Optional<String> serverType = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
                    Optional<String> lobbyName = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
                    Optional<String> mode = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
                    Optional<String> map = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf);
                    return new LocationUpdateS2CPacket(true, ver, serverName, serverType, lobbyName, mode, map);
                } catch (Throwable t) {
                    return new LocationUpdateS2CPacket(false, -1, "", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                } finally {
                    buf.readerIndex(buf.writerIndex()); // Consume all bytes to prevent Netty decoder overflow exception
                }
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, LocationUpdateS2CPacket value) {
                throw new UnsupportedOperationException("S2C packet encode not supported");
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }
}
