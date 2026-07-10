package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.client.config.VoxyConfigMenu;
import me.cortex.voxy.client.config.SodiumConfigBuilder;
import me.cortex.voxy.addon.hypixel.AddonConfig;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.lang.reflect.Field;

@Mixin(value = VoxyConfigMenu.class, remap = false)
public class MixinVoxyConfigMenu {

    @ModifyArg(
        method = "registerConfigLate",
        at = @At(
            value = "INVOKE",
            target = "Lme/cortex/voxy/client/config/SodiumConfigBuilder;buildToSodium(Lnet/caffeinemc/mods/sodium/api/config/structure/ConfigBuilder;Lnet/caffeinemc/mods/sodium/api/config/structure/ModOptionsBuilder;Lnet/caffeinemc/mods/sodium/api/config/StorageEventHandler;Ljava/util/function/Consumer;[Lme/cortex/voxy/client/config/SodiumConfigBuilder$Page;)V"
        ),
        index = 4
    )
    private static SodiumConfigBuilder.Page[] voxy$injectFastReloadSetting(SodiumConfigBuilder.Page[] pages) {
        if (pages != null && pages.length > 0) {
            try {
                var generalPage = pages[0];
                Field groupsField = SodiumConfigBuilder.Page.class.getDeclaredField("groups");
                groupsField.setAccessible(true);
                SodiumConfigBuilder.Group[] oldGroups = (SodiumConfigBuilder.Group[]) groupsField.get(generalPage);

                // Instantiated reflectively to bypass compile-time transitive dependency check on Sodium's BooleanOptionBuilder
                Class<?> boolOptionClass = Class.forName("me.cortex.voxy.client.config.SodiumConfigBuilder$BoolOption");
                java.lang.reflect.Constructor<?> boolOptCons = boolOptionClass.getConstructor(
                    String.class, Component.class, java.util.function.Supplier.class, java.util.function.Consumer.class
                );

                java.util.function.Supplier<Boolean> getter = () -> AddonConfig.isFastReloads();
                java.util.function.Consumer<Boolean> setter = v -> AddonConfig.setFastReloads(v);

                Object fastReloadsOption = boolOptCons.newInstance(
                    "voxyaddon:fast_reloads",
                    Component.literal("Addon: Force Faster Reloads"),
                    getter,
                    setter
                );

                java.util.function.Function<Object, Component> tooltipSupplier = op -> Component.literal("Skips thread-blocking GC and GPU synchronization during area switches on Hypixel to eliminate screen freezes.");
                java.lang.reflect.Method setTooltipSupplierMethod = boolOptionClass.getMethod("setTooltipSupplier", java.util.function.Function.class);
                fastReloadsOption = setTooltipSupplierMethod.invoke(fastReloadsOption, tooltipSupplier);

                java.lang.reflect.Method setEnablerMethod = boolOptionClass.getMethod("setEnabler", String.class);
                fastReloadsOption = setEnablerMethod.invoke(fastReloadsOption, "voxy:enabled");

                Class<?> groupClass = Class.forName("me.cortex.voxy.client.config.SodiumConfigBuilder$Group");
                Class<?> optionClass = Class.forName("me.cortex.voxy.client.config.SodiumConfigBuilder$Option");
                java.lang.reflect.Constructor<?> groupCons = groupClass.getConstructor(
                    java.lang.reflect.Array.newInstance(optionClass, 0).getClass()
                );

                Object optionsArray = java.lang.reflect.Array.newInstance(optionClass, 1);
                java.lang.reflect.Array.set(optionsArray, 0, fastReloadsOption);

                SodiumConfigBuilder.Group addonGroup = (SodiumConfigBuilder.Group) groupCons.newInstance(optionsArray);

                SodiumConfigBuilder.Group[] newGroups = new SodiumConfigBuilder.Group[oldGroups.length + 1];
                System.arraycopy(oldGroups, 0, newGroups, 0, oldGroups.length);
                newGroups[oldGroups.length] = addonGroup;

                groupsField.set(generalPage, newGroups);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        return pages;
    }
}
