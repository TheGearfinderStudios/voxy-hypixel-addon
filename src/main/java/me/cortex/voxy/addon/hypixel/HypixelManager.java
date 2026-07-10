package me.cortex.voxy.addon.hypixel;

import net.azureaaron.hmapi.events.HypixelPacketEvents;
import net.azureaaron.hmapi.network.packet.v1.s2c.LocationUpdateS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.network.chat.Component;
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
        AddonConfig.load();

        registerClientCommandReflectively();

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

    @SuppressWarnings("unchecked")
    private static void registerClientCommandReflectively() {
        try {
            Class<?> callbackClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback");
            Class<?> managerClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.ClientCommandManager");
            Class<?> sourceClass = Class.forName("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource");

            java.lang.reflect.Method literalMethod = managerClass.getMethod("literal", String.class);
            java.lang.reflect.Method argumentMethod = managerClass.getMethod("argument", String.class, com.mojang.brigadier.arguments.ArgumentType.class);

            java.lang.reflect.Field eventField = callbackClass.getField("EVENT");
            Object event = eventField.get(null);
            java.lang.reflect.Method registerMethod = event.getClass().getMethod("register", callbackClass);

            Object callbackProxy = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.getClassLoader(),
                new Class<?>[]{callbackClass},
                (proxy, method, args) -> {
                    if (method.getName().equals("register")) {
                        com.mojang.brigadier.CommandDispatcher<Object> dispatcher = (com.mojang.brigadier.CommandDispatcher<Object>) args[0];
                        
                        var voxyaddon = (com.mojang.brigadier.builder.LiteralArgumentBuilder<Object>) literalMethod.invoke(null, "voxyaddon");
                        var fastreloads = (com.mojang.brigadier.builder.LiteralArgumentBuilder<Object>) literalMethod.invoke(null, "fastreloads");
                        var enabledArg = (com.mojang.brigadier.builder.RequiredArgumentBuilder<Object, Boolean>) argumentMethod.invoke(
                            null, "enabled", com.mojang.brigadier.arguments.BoolArgumentType.bool()
                        );

                        enabledArg.executes(context -> {
                            boolean enabled = com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "enabled");
                            AddonConfig.setFastReloads(enabled);
                            
                            Object source = context.getSource();
                            try {
                                java.lang.reflect.Method sendFeedbackMethod = sourceClass.getMethod("sendFeedback", net.minecraft.network.chat.Component.class);
                                sendFeedbackMethod.invoke(source, Component.literal("Voxy fast reloads set to: " + enabled));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return 1;
                        });

                        fastreloads.executes(context -> {
                            Object source = context.getSource();
                            try {
                                java.lang.reflect.Method sendFeedbackMethod = sourceClass.getMethod("sendFeedback", net.minecraft.network.chat.Component.class);
                                sendFeedbackMethod.invoke(source, Component.literal("Voxy fast reloads is currently: " + AddonConfig.isFastReloads()));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return 1;
                        });

                        fastreloads.then(enabledArg);
                        voxyaddon.then(fastreloads);

                        dispatcher.register(voxyaddon);
                    }
                    return null;
                }
            );

            registerMethod.invoke(event, callbackProxy);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void scheduleReload(String gamemode, String area) {
        cancelPendingReload();
        
        // Condition 2: The Debounce (200ms)
        // This prevents "triple reloads" when Hypixel spams packets during island jumps.
        pendingReload = scheduler.schedule(() -> {
            Minecraft.getInstance().execute(() -> {
                Logger.info(String.format("[Voxy-Addon] Rebooting renderer for new area -> Type: %s | Folder: %s", 
                    gamemode, area));
                
                long startTime = System.currentTimeMillis();
                var lr = Minecraft.getInstance().levelRenderer;
                if (lr instanceof IGetVoxyRenderSystem getter) {
                    // Condition 1: "Is-Loading" Check / Safety
                    // Voxy's shutdown/create sequence is heavy; executing it here 
                    // ensures we are on the Render Thread and after the debounce.
                    getter.voxy$shutdownRenderer();
                    long shutdownTime = System.currentTimeMillis();
                    
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