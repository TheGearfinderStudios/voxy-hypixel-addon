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
    private static String activeSkyblockArea = null;
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
                activeSkyblockArea = null;
                cancelPendingReload();
            });
        });

        // HM API listeners
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) {
                String area = location.mode().orElse(null);
                
                Minecraft.getInstance().execute(() -> {
                    if (!isHypixel) return;
                    
                    if (!Objects.equals(activeSkyblockArea, area)) {
                        String oldArea = activeSkyblockArea;
                        activeSkyblockArea = area;
                        
                        // Condition 3: Comparison Gating
                        // Only reload if we are transitioning between two known states, 
                        // or from Gating (null) to an actual area.
                        // We skip if oldArea was null and new area is null (no change).
                        if (area != null || oldArea != null) {
                            scheduleReload(area);
                        }
                    }
                });
            }
        });
    }

    private static void scheduleReload(String area) {
        cancelPendingReload();
        
        // Condition 2: The Debounce (200ms)
        // This prevents "triple reloads" when Hypixel spams packets during island jumps.
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Logger.info("[Voxy-Addon] Rebooting Voxy for area: " + (area == null ? "Limbo/Other" : area));
                
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
            activeSkyblockArea = null;
            if (isHypixel) {
                Logger.info("[Voxy-Addon] Hypixel Detected. Gating active.");
            }
        } else {
            isHypixel = false;
            activeSkyblockArea = null;
        }
    }

    public static boolean isHypixel() {
        return isHypixel;
    }

    public static String getAreaId() {
        return activeSkyblockArea;
    }
}
