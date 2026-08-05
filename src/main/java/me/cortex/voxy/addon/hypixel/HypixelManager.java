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
    private static String activeGamemodeArea = null;
    private static String rawActiveArea = null;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingReload = null;

    // Whether WE own the C2S register payload type. If another mod (e.g. official
    // Hypixel Mod API bundled in blade-addons) registered it first, they handle
    // sending the register packet and we skip it. S2C is handled by the mixin
    // interceptor unconditionally, so no equivalent flag is needed there.
    private static boolean weOwnC2SRegistration = false;

    @Override
    public void onInitializeClient() {
        AddonConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> scheduler.shutdownNow());

        // S2C types: registered so Fabric can dispatch our receivers.
        // If another mod registered first, we catch and skip — the mixin interceptor
        // on ClientboundCustomPayloadPacket guarantees our receivers still fire regardless.
        try {
            PayloadTypeRegistry.clientboundPlay().register(HelloS2CPacket.ID, HelloS2CPacket.PACKET_CODEC);
        } catch (IllegalArgumentException ignored) {}
        try {
            PayloadTypeRegistry.clientboundPlay().register(LocationUpdateS2CPacket.ID, LocationUpdateS2CPacket.PACKET_CODEC);
        } catch (IllegalArgumentException ignored) {}

        // C2S: if another mod owns this, they send the register packet — we skip sending.
        try {
            PayloadTypeRegistry.serverboundPlay().register(RegisterC2SPacket.ID, RegisterC2SPacket.PACKET_CODEC);
            weOwnC2SRegistration = true;
        } catch (IllegalArgumentException ignored) {}

        // Hello: set isHypixel and request location events
        ClientPlayNetworking.registerGlobalReceiver(HelloS2CPacket.ID, (payload, context) -> {
            if (!payload.success()) return;
            isHypixel = true;
            Logger.info("[Voxy-Addon] Hypixel Hello received. Subscribing to location updates...");
            sendLocationRegistration(context.responseSender());
        });

        // Location update
        ClientPlayNetworking.registerGlobalReceiver(LocationUpdateS2CPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                isHypixel = true;
                if (!payload.success()) return;

                String serverType = payload.serverType().orElse("");
                String mode = payload.mode().orElse("");
                String map = payload.map().orElse("");

                String normalized = null;
                if (!serverType.isEmpty()) {
                    normalized = serverType;
                    if (!mode.isEmpty()) normalized += "_" + mode;
                    else if (!map.isEmpty()) normalized += "_" + map;
                }

                String rawArea = normalized;
                if (normalized != null) normalized = AddonConfig.getCanonicalAreaId(normalized);

                Logger.info(String.format("[Voxy-Addon] Location -> Type: %s | Mode: %s | Map: %s | Normalized: %s",
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
                if (isHypixel) sendLocationRegistration(sender);
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
        if (!weOwnC2SRegistration) return; // another mod handles the handshake
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
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Logger.info(String.format("[Voxy-Addon] Rebooting renderer -> Type: %s | Area: %s | Folder: %s",
                        gamemode, rawArea, canonicalArea));

                long startTime = System.currentTimeMillis();
                var lr = Minecraft.getInstance().levelRenderer;
                if (lr instanceof IVoxyRenderSystemHolder getter) {
                    getter.voxy$shutdownRenderer();
                    long shutdownTime = System.currentTimeMillis();
                    getter.voxy$setWorld(Minecraft.getInstance().level);
                    getter.voxy$createRenderer();
                    long createTime = System.currentTimeMillis();
                    Logger.info(String.format("[Voxy-Addon-Benchmark] Reload: %d ms (Shutdown: %d ms, Create: %d ms). FastReloads=%s",
                            (createTime - startTime), (shutdownTime - startTime), (createTime - shutdownTime), AddonConfig.isFastReloads()));
                }
                pendingReload = null;
            });
        }, 200, TimeUnit.MILLISECONDS);
    }

    private static void cancelPendingReload() {
        if (pendingReload != null && !pendingReload.isDone()) pendingReload.cancel(false);
    }

    private static void onJoin(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        String ip = (serverData != null && serverData.ip != null) ? serverData.ip.toLowerCase() : "";
        String brand = (client.player != null && client.player.connection != null && client.player.connection.serverBrand() != null)
                ? client.player.connection.serverBrand() : "";
        isHypixel = ip.contains("hypixel.net") || ip.contains("hypixel.io") || brand.contains("Hypixel");
        activeGamemodeArea = null;
        rawActiveArea = null;
    }

    public static boolean isHypixel() { return isHypixel; }
    public static String getAreaId() { return activeGamemodeArea; }
    public static String getRawAreaId() { return rawActiveArea; }

    public static void beginLevelTransition() {
        if (!isHypixel) return;
        activeGamemodeArea = null;
        rawActiveArea = null;
        cancelPendingReload();
    }
}
