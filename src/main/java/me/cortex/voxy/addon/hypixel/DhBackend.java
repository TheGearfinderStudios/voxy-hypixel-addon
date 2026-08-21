package me.cortex.voxy.addon.hypixel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * DH backend: sets per-area storage keys in DH's KeyedClientLevelManager via reflection.
 * Imports no DH classes directly so this class is safe to load even when DH is absent.
 */
public class DhBackend {
    private static final boolean DH_LOADED = FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static final Logger LOGGER = LoggerFactory.getLogger("voxy-hypixel-addon/dh");

    private static final String HYPIXEL_SERVER_KEY = "hypixel";

    private static boolean initialized = false;
    private static Object managerInstance = null;
    private static Method setServerKeyedLevelMethod = null;
    private static Method clearKeyedLevelMethod = null;
    private static Method getWrapperMethod = null;
    private static Method getDhWorldMethod = null;
    private static Method getOrLoadLevelMethod = null;

    private static void sendChatError(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("[Voxy & DH Addon] " + message)
                        .withStyle(s -> s.withColor(0xFF5555)));
                }
            });
        }
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!DH_LOADED) return;
        try {
            // 1. Get SingletonInjector INSTANCE
            Class<?> injectorClass = Class.forName("com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector");
            Object injector = injectorClass.getField("INSTANCE").get(null);

            // 2. Resolve IKeyedClientLevelManager instance
            Class<?> managerInterface = Class.forName("com.seibel.distanthorizons.core.level.IKeyedClientLevelManager");
            Method getMethod = injectorClass.getMethod("get", Class.class);
            managerInstance = getMethod.invoke(injector, managerInterface);

            // 3. Resolve setServerKeyedLevel and clearKeyedLevel methods
            Class<?> levelWrapperInterface = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper");
            setServerKeyedLevelMethod = managerInterface.getMethod("setServerKeyedLevel",
                levelWrapperInterface, String.class, String.class, String.class);
            clearKeyedLevelMethod = managerInterface.getMethod("clearKeyedLevel");

            // 4. Resolve ClientLevelWrapper.getWrapper(ClientLevel, boolean)
            Class<?> clientLevelWrapperClass = Class.forName("com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper");
            getWrapperMethod = clientLevelWrapperClass.getMethod("getWrapper", ClientLevel.class, boolean.class);

            // 5. Resolve SharedApi.getAbstractDhWorld() and getOrLoadLevel
            Class<?> sharedApiClass = Class.forName("com.seibel.distanthorizons.core.api.internal.SharedApi");
            getDhWorldMethod = sharedApiClass.getMethod("getAbstractDhWorld");
            Class<?> baseLevelWrapperInterface = Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper");
            Class<?> dhWorldInterface = Class.forName("com.seibel.distanthorizons.core.world.IDhWorld");
            getOrLoadLevelMethod = dhWorldInterface.getMethod("getOrLoadLevel", baseLevelWrapperInterface);

            LOGGER.info("[Voxy-Addon/DH] KeyedClientLevelManager & SharedApi initialized via reflection.");
        } catch (Throwable t) {
            LOGGER.error("[Voxy-Addon/DH] Failed to initialize DH reflection - area isolation disabled.", t);
            sendChatError("Failed to hook into Distant Horizons internals: " + t.getClass().getSimpleName() + " (check logs)");
        }
    }

    public static void onAreaChange(String canonicalAreaId) {
        if (!DH_LOADED) return;
        init();
        if (managerInstance == null || setServerKeyedLevelMethod == null) return;
        try {
            ClientLevel clientLevel = Minecraft.getInstance().level;
            if (clientLevel == null) return;

            Object wrappedLevel = null;
            if (getWrapperMethod != null) {
                wrappedLevel = getWrapperMethod.invoke(null, clientLevel, true);
            }

            String dimensionResource = "minecraft:overworld";
            if (wrappedLevel != null) {
                try {
                    Method getDimMethod = wrappedLevel.getClass().getMethod("getDimensionName");
                    dimensionResource = (String) getDimMethod.invoke(wrappedLevel);
                } catch (Throwable ignored) {}
            }

            Object keyedLevel = setServerKeyedLevelMethod.invoke(managerInstance, wrappedLevel, dimensionResource, HYPIXEL_SERVER_KEY, canonicalAreaId);
            LOGGER.info("[Voxy-Addon/DH] Set level key -> {} (dim: {})", canonicalAreaId, dimensionResource);

            if (keyedLevel != null && getDhWorldMethod != null && getOrLoadLevelMethod != null) {
                Object world = getDhWorldMethod.invoke(null);
                if (world != null) {
                    getOrLoadLevelMethod.invoke(world, keyedLevel);
                    LOGGER.info("[Voxy-Addon/DH] Loaded keyed level into DH world -> {}", canonicalAreaId);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("[Voxy-Addon/DH] Failed to set level key", t);
            sendChatError("Failed to switch DH area: " + t.getMessage());
        }
    }

    public static void onDisconnect() {
        if (!DH_LOADED || managerInstance == null || clearKeyedLevelMethod == null) return;
        try {
            clearKeyedLevelMethod.invoke(managerInstance);
            LOGGER.info("[Voxy-Addon/DH] Cleared level keys on disconnect.");
        } catch (Throwable t) {
            LOGGER.error("[Voxy-Addon/DH] Failed to clear level key", t);
        }
    }
}
