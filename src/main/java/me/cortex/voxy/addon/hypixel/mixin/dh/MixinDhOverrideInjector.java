package me.cortex.voxy.addon.hypixel.mixin.dh;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Prevents DH from throwing "IllegalStateException: An override already exists with the priority [10]"
 * when Iris re-registers render setup overrides across preserved dimension pipelines.
 */
@Mixin(targets = "com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector", remap = false)
public class MixinDhOverrideInjector {

    @Inject(method = "bind", at = @At("HEAD"), require = 0)
    private void voxy_hypixel_addon$cleanDuplicateOverride(Class<?> dependencyInterface, @Coerce Object dependencyImplementation, CallbackInfo ci) {
        if (dependencyImplementation != null && dependencyInterface != null) {
            try {
                Method getPriorityMethod = dependencyImplementation.getClass().getMethod("getPriority");
                int priority = (int) getPriorityMethod.invoke(dependencyImplementation);

                Method getMethod = this.getClass().getMethod("get", Class.class, int.class);
                Object existing = getMethod.invoke(this, dependencyInterface, priority);
                if (existing != null) {
                    Class<?> overrideableClass = Class.forName("com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IDhApiOverrideable");
                    Method unbindMethod = this.getClass().getMethod("unbind", Class.class, overrideableClass);
                    unbindMethod.invoke(this, dependencyInterface, existing);
                }
            } catch (Throwable ignored) {}
        }
    }
}
