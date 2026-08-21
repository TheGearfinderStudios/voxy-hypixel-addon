package me.cortex.voxy.addon.hypixel;

import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HypixelManager implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("voxy-hypixel-addon");

    private static final boolean VOXY_LOADED = FabricLoader.getInstance().isModLoaded("voxy");
    private static final boolean DH_LOADED = FabricLoader.getInstance().isModLoaded("distanthorizons");

    private static boolean isHypixel = false;
    private static String activeGamemodeArea = null;
    private static String rawActiveArea = null;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingReload = null;

    @Override
    public void onInitializeClient() {
        AddonConfig.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> scheduler.shutdownNow());

        // Register for Hypixel API location updates
        it.unimi.dsi.fastutil.objects.Object2IntMap<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<net.azureaaron.hmapi.network.packet.s2c.HypixelS2CPacket>> events = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<>();
        events.put(LocationUpdateS2CPacket.ID, 1);
        net.azureaaron.hmapi.network.HypixelNetworking.registerToEvents(events);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> onJoin(client));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                isHypixel = false;
                activeGamemodeArea = null;
                rawActiveArea = null;
                cancelPendingReload();
                DhBackend.onDisconnect();
            });
        });

        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) {
                Minecraft.getInstance().execute(() -> {
                    if (!isHypixel) return;

                    String serverType = location.serverType().orElse("");
                    String mode = location.mode().orElse("");
                    String map = location.map().orElse("");

                    String normalized = null;
                    if (!serverType.isEmpty()) {
                        normalized = serverType;
                        if (!mode.isEmpty()) {
                            normalized += "_" + mode;
                        } else if (!map.isEmpty()) {
                            normalized += "_" + map;
                        }
                    }

                    String rawArea = normalized;
                    if (normalized != null) {
                        normalized = AddonConfig.getCanonicalAreaId(normalized);
                    }

                    if (!Objects.equals(activeGamemodeArea, normalized)) {
                        activeGamemodeArea = normalized;
                        rawActiveArea = rawArea;
                        scheduleReload(serverType, rawArea, normalized);
                    } else {
                        rawActiveArea = rawArea;
                    }
                });
            }
        });
    }

    private static void scheduleReload(String gamemode, String rawArea, String canonicalArea) {
        cancelPendingReload();
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                LOGGER.info("[Voxy-Addon] Area change -> Type: {} | Area: {} | Folder: {}", gamemode, rawArea, canonicalArea);

                if (VOXY_LOADED) VoxyBackend.onAreaChange(gamemode, rawArea, canonicalArea);
                if (DH_LOADED) DhBackend.onAreaChange(canonicalArea);

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
            activeGamemodeArea = null;
            rawActiveArea = null;

            if (isHypixel && !VOXY_LOADED && !DH_LOADED) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal(
                            "[Voxy & DH Hypixel Addon] Neither Voxy nor Distant Horizons is installed - this addon has no effect!"
                        ).withStyle(s -> s.withColor(0xFF4444)));
                    }
                });
            }
        } else {
            isHypixel = false;
            activeGamemodeArea = null;
            rawActiveArea = null;
        }
    }

    public static boolean isHypixel() {
        if (isHypixel) return true;
        return FlashbackAddonCompat.isHypixelReplay();
    }

    public static String getAreaId() {
        if (activeGamemodeArea != null) return activeGamemodeArea;
        if (FlashbackAddonCompat.isHypixelReplay()) {
            return FlashbackAddonCompat.getReplayHypixelArea();
        }
        return null;
    }

    public static String getRawAreaId() {
        if (rawActiveArea != null) return rawActiveArea;
        if (FlashbackAddonCompat.isHypixelReplay()) {
            return FlashbackAddonCompat.getReplayHypixelRawArea();
        }
        return null;
    }

    public static void beginLevelTransition() {
        if (!isHypixel()) return;
        activeGamemodeArea = null;
        rawActiveArea = null;
        cancelPendingReload();
    }
}
