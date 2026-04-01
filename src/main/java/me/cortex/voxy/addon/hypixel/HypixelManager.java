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

public class HypixelManager implements ClientModInitializer {
    private static boolean isHypixel = false;
    private static String activeSkyblockArea = null;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onJoin(client);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            isHypixel = false;
            activeSkyblockArea = null;
        });

        // HM API listeners
        HypixelPacketEvents.LOCATION_UPDATE.register(packet -> {
            if (packet instanceof LocationUpdateS2CPacket location) {
                String prevArea = activeSkyblockArea;
                activeSkyblockArea = location.mode().orElse(null);
                String server = location.serverName();
                
                if (isHypixel && !Objects.equals(prevArea, activeSkyblockArea)) {
                    Logger.info("[Voxy-Addon] Hypixel Area Change: " + activeSkyblockArea + " (Server: " + server + ")");
                    // Trigger renderer reload
                    Minecraft.getInstance().execute(() -> {
                        var lr = Minecraft.getInstance().levelRenderer;
                        if (lr instanceof IGetVoxyRenderSystem getter) {
                            getter.shutdownRenderer();
                            getter.createRenderer();
                        }
                    });
                }
            }
        });
    }

    private static void onJoin(Minecraft client) {
        ServerData serverData = client.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            String ip = serverData.ip.toLowerCase();
            isHypixel = ip.contains("hypixel.net");
            activeSkyblockArea = null;
            if (isHypixel) {
                Logger.info("[Voxy-Addon] Hypixel Detected. Gating active until Skyblock area is confirmed.");
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
