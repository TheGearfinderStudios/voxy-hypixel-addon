package me.cortex.voxy.addon.hypixel;

import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HypixelManager implements ClientModInitializer {
    private static boolean isHypixel = false;
    private static String activeGamemodeArea = null;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> pendingReload = null;

    @Override
    public void onInitializeClient() {
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

        // HM API listeners
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) {
                Minecraft.getInstance().execute(() -> {
                    if (!isHypixel) return;

                    String serverType = location.serverType().orElse("");
                    String mode = location.mode().orElse("");
                    String map = location.map().orElse("");

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
            }
        });
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
                    getter.shutdownRenderer();
                    getter.createRenderer();
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