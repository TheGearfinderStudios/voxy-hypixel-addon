package me.cortex.voxy.addon.hypixel;

import me.cortex.voxy.addon.hypixel.network.HypixelPayloads.HelloS2CPacket;
import me.cortex.voxy.addon.hypixel.network.HypixelPayloads.LocationUpdateS2CPacket;
import me.cortex.voxy.addon.hypixel.network.HypixelPayloads.RegisterC2SPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HypixelManager implements ClientModInitializer {
    private static boolean isHypixel = false;
    private static boolean weOwnC2SRegistration = false;
    private static String activeGamemodeArea = null;
    private static String rawActiveArea = null;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingReload = null;

    @Override
    public void onInitializeClient() {
        AddonConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> scheduler.shutdownNow());

        // Register custom payload codecs with Fabric API (safely ignoring duplicates if another mod registered them first)
        try {
            PayloadTypeRegistry.clientboundPlay().register(HelloS2CPacket.ID, HelloS2CPacket.PACKET_CODEC);
        } catch (IllegalArgumentException ignored) {}
        try {
            PayloadTypeRegistry.clientboundPlay().register(LocationUpdateS2CPacket.ID, LocationUpdateS2CPacket.PACKET_CODEC);
        } catch (IllegalArgumentException ignored) {}
        try {
            PayloadTypeRegistry.serverboundPlay().register(RegisterC2SPacket.ID, RegisterC2SPacket.PACKET_CODEC);
            weOwnC2SRegistration = true;
        } catch (IllegalArgumentException ignored) {} // another mod owns this (e.g. official Hypixel Mod API) — they handle the handshake

        // Register networking receivers
        ClientPlayNetworking.registerGlobalReceiver(HelloS2CPacket.ID, (payload, context) -> {
            if (!payload.success()) return;
            isHypixel = true;
            Logger.info("[Voxy-Addon] Hypixel Hello received. Subscribing to location updates...");
            sendLocationRegistration(context.responseSender());
        });

        ClientPlayNetworking.registerGlobalReceiver(LocationUpdateS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                isHypixel = true;
                if (!payload.success()) return;

                String serverType = payload.serverType().orElse("");
                String mode = payload.mode().orElse("");
                String map = payload.map().orElse("");

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

                String rawArea = normalized;
                if (normalized != null) {
                    normalized = AddonConfig.getCanonicalAreaId(normalized);
                }

                Logger.info(String.format("[Voxy-Addon] Location packet received -> Type: %s | Mode: %s | Map: %s | Normalized: %s", 
                    serverType, mode, map, normalized));

                if (!Objects.equals(activeGamemodeArea, normalized)) {
                    activeGamemodeArea = normalized;
                    rawActiveArea = rawArea;
                    scheduleReload(serverType, rawArea, normalized);
                } else {
                    rawActiveArea = rawArea;
                }
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                onJoin(client);
                if (isHypixel) {
                    sendLocationRegistration(sender);
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                isHypixel = false;
                activeGamemodeArea = null;
                rawActiveArea = null;
                cancelPendingReload();
            });
        });
    }

    private static void sendLocationRegistration(PacketSender sender) {
        if (!weOwnC2SRegistration) return; // another mod owns hypixel:register, location packets arrive without us sending it
        try {
            Map<Identifier, Integer> events = new HashMap<>();
            events.put(LocationUpdateS2CPacket.ID.id(), 1);
            sender.sendPacket(new RegisterC2SPacket(1, events));
        } catch (Throwable t) {
            Logger.error("[Voxy-Addon] Failed to send location registration packet", t);
        }
    }

    private static void scheduleReload(String gamemode, String rawArea, String canonicalArea) {
        cancelPendingReload();
        
        // Condition 2: The Debounce (200ms)
        // This prevents "triple reloads" when Hypixel spams packets during island jumps.
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Logger.info(String.format("[Voxy-Addon] Rebooting renderer for new area -> Type: %s | Area: %s | Folder: %s", 
                    gamemode, rawArea, canonicalArea));
                
                long startTime = System.currentTimeMillis();
                var lr = Minecraft.getInstance().levelRenderer;
                if (lr instanceof IVoxyRenderSystemHolder getter) {
                    // Condition 1: "Is-Loading" Check / Safety
                    // Voxy's shutdown/create sequence is heavy; executing it here 
                    // ensures we are on the Render Thread and after the debounce.
                    getter.voxy$shutdownRenderer();
                    long shutdownTime = System.currentTimeMillis();
                    
                    getter.voxy$setWorld(Minecraft.getInstance().level);
                    getter.voxy$createRenderer();
                    long createTime = System.currentTimeMillis();
                    
                    Logger.info(String.format("[Voxy-Addon-Benchmark] Reload completed in %d ms (Shutdown: %d ms, Create: %d ms). Fast Reloads = %s", 
                        (createTime - startTime), (shutdownTime - startTime), (createTime - shutdownTime), AddonConfig.isFastReloads()));
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
        String ip = (serverData != null && serverData.ip != null) ? serverData.ip.toLowerCase() : "";
        String brand = (client.player != null && client.player.connection != null && client.player.connection.serverBrand() != null) ? client.player.connection.serverBrand() : "";
        isHypixel = ip.contains("hypixel.net") || ip.contains("hypixel.io") || brand.contains("Hypixel");
        activeGamemodeArea = null; 
        rawActiveArea = null;
    }

    public static boolean isHypixel() {
        return isHypixel;
    }

    public static String getAreaId() {
        return activeGamemodeArea;
    }

    public static String getRawAreaId() {
        return rawActiveArea;
    }

    public static void beginLevelTransition() {
        if (!isHypixel) return;

        // A new ClientLevel arrives before HM API's location packet; null the area
        // so Voxy doesn't build an expensive renderer for the old island before scheduleReload() tears it down.
        activeGamemodeArea = null;
        rawActiveArea = null;
        cancelPendingReload();
    }
}
