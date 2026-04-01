package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Level.class, priority = 2000)
public abstract class MixinWorld {
    @Shadow(remap = false) private WorldIdentifier identifier;

    /**
     * @author Antigravity
     * @reason Add hypixel area support to world identification
     */
    @Overwrite(remap = false)
    public WorldIdentifier voxy$getIdentifier() {
        WorldIdentifier id = this.identifier;
        if (id != null && HypixelManager.isHypixel()) {
            String areaId = HypixelManager.getAreaId();
            if (areaId == null) {
                return null; // Gating: Return null if on hypixel but no area confirmed
            }
            // Add subId to the identifier
            ((MixinWorldIdentifier)(Object)id).voxy_hypixel_addon$subId = areaId;
        }
        return id;
    }
}
