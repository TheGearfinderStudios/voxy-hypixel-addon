package me.cortex.voxy.addon.hypixel;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HypixelManager implements ClientModInitializer {
    private static boolean isHypixel = false;
    private static String activeGamemodeArea = null;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingReload = null;

    public record HelloPayload(int environmentOrdinal) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<HelloPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hypixel", "hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                throw new UnsupportedOperationException();
            },
            buf -> {
                boolean success = buf.readBoolean();
                int ordinal = success ? buf.readVarInt() : -1;
                buf.readerIndex(buf.writerIndex()); // Consume remaining bytes to prevent kick
                return new HelloPayload(ordinal);
            }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record LocationUpdatePayload(String serverName, Optional<String> serverType, Optional<String> lobbyName, Optional<String> mode, Optional<String> map) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LocationUpdatePayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hyevent", "location"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LocationUpdatePayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                throw new UnsupportedOperationException();
            },
            buf -> {
                boolean success = buf.readBoolean();
                if (!success) {
                    buf.readerIndex(buf.writerIndex());
                    return new LocationUpdatePayload("", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                }
                buf.readVarInt(); // version
                String serverName = buf.readUtf();
                Optional<String> serverType = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                Optional<String> lobbyName = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                Optional<String> mode = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                Optional<String> map = buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty();
                buf.readerIndex(buf.writerIndex()); // Consume remaining bytes to prevent kick
                return new LocationUpdatePayload(serverName, serverType, lobbyName, mode, map);
            }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record RegisterPayload(int version, Map<Identifier, Integer> eventsToRegister) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<RegisterPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hypixel", "register"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RegisterPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.version());
                buf.writeVarInt(value.eventsToRegister().size());
                for (Map.Entry<Identifier, Integer> entry : value.eventsToRegister().entrySet()) {
                    Identifier.STREAM_CODEC.encode(buf, entry.getKey());
                    buf.writeVarInt(entry.getValue());
                }
            },
            buf -> {
                throw new UnsupportedOperationException();
            }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    @Override
    public void onInitializeClient() {
        AddonConfig.load();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("voxyaddon")
                .then(ClientCommandManager.literal("fastreloads")
                    .then(ClientCommandManager.argument("enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                            AddonConfig.setFastReloads(enabled);
                            context.getSource().sendFeedback(Component.literal("Voxy fast reloads set to: " + enabled));
                            return 1;
                        })
                    )
                    .executes(context -> {
                        context.getSource().sendFeedback(Component.literal("Voxy fast reloads is currently: " + AddonConfig.isFastReloads()));
                        return 1;
                    })
                )
            );
        });

        // Register custom payload codecs safely
        try {
            PayloadTypeRegistry.playS2C().register(HelloPayload.ID, HelloPayload.CODEC);
        } catch (Exception ignored) {}
        try {
            PayloadTypeRegistry.playS2C().register(LocationUpdatePayload.ID, LocationUpdatePayload.CODEC);
        } catch (Exception ignored) {}
        try {
            PayloadTypeRegistry.playC2S().register(RegisterPayload.ID, RegisterPayload.CODEC);
        } catch (Exception ignored) {}

        // Listen for Hypixel packets
        ClientPlayNetworking.registerGlobalReceiver(HelloPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (isHypixel) {
                    Map<Identifier, Integer> registration = new HashMap<>();
                    registration.put(LocationUpdatePayload.ID.id(), 1);
                    ClientPlayNetworking.send(new RegisterPayload(1, registration));
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(LocationUpdatePayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (!isHypixel) return;

                String serverType = getReflectedString(payload, "serverType", "getServerType");
                String mode = getReflectedString(payload, "mode", "getMode");
                String map = getReflectedString(payload, "map", "getMap");

                String normalized = null;

                // Only process if we have a valid serverType foundation
                if (!serverType.isEmpty()) {
                    normalized = serverType; // Base (e.g., "SKYBLOCK" or "MAIN")
                    
                    // Append the most specific sub-location
                    if (!mode.isEmpty()) {
                        normalized += "_" + mode; // -> "SKYBLOCK_foraging_2"
                    } else if (!map.isEmpty()) {
                        normalized += "_" + map;  // -> "HOUSING_Base"
                    }
                }

                if (!Objects.equals(activeGamemodeArea, normalized)) {
                    activeGamemodeArea = normalized;
                    scheduleReload(serverType, normalized);
                }
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> onJoin(client));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                isHypixel = false;
                activeGamemodeArea = null;
                cancelPendingReload();
            });
        });
    }

    private static String getReflectedString(Object obj, String recordMethod, String getterMethod) {
        try {
            java.lang.reflect.Method method;
            try {
                method = obj.getClass().getMethod(recordMethod);
            } catch (NoSuchMethodException e) {
                method = obj.getClass().getMethod(getterMethod);
            }
            Object res = method.invoke(obj);
            if (res instanceof Optional<?> opt) {
                Object optVal = opt.orElse(null);
                if (optVal == null) return "";
                if (optVal instanceof Enum<?> e) {
                    return e.name();
                }
                return optVal.toString();
            } else if (res instanceof String str) {
                return str;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static void scheduleReload(String gamemode, String area) {
        cancelPendingReload();
        
        // Condition 2: The Debounce (200ms)
        // This prevents "triple reloads" when Hypixel spams packets during island jumps.
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Logger.info(String.format("[Voxy-Addon] Rebooting renderer for new area -> Type: %s | Folder: %s", 
                    gamemode, area));
                
                var lr = Minecraft.getInstance().levelRenderer;
                if (lr instanceof IGetVoxyRenderSystem getter) {
                    // Condition 1: "Is-Loading" Check / Safety
                    // Voxy's shutdown/create sequence is heavy; executing it here 
                    // ensures we are on the Render Thread and after the debounce.
                    getter.voxy$shutdownRenderer();
                    getter.voxy$createRenderer();
                }
                pendingReload = null;
            });
        }, 200, TimeUnit.MILLISECONDS);
    }

    private static void cancelPendingReload() {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }
    }

    private static void onJoin(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            String ip = serverData.ip.toLowerCase();
            isHypixel = ip.contains("hypixel.net");
            // Set to null on join to ensure gating until HM-API provides location
            activeGamemodeArea = null; 
            //if (isHypixel) Logger.info("[Voxy-Addon] Hypixel Detected. Gating active.");
        } else {
            isHypixel = false;
            activeGamemodeArea = null;
        }
    }

    public static boolean isHypixel() {
        return isHypixel;
    }

    public static String getAreaId() {
        return activeGamemodeArea;
    }
}