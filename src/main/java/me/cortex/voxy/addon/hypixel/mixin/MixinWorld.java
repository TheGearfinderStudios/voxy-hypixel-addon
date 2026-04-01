package me.cortex.voxy.addon.hypixel.mixin;

import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.addon.hypixel.access.IPerAreaWorldIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = Level.class, priority = 2000)
public abstract class MixinWorld {
    @Shadow(remap = false) private WorldIdentifier identifier;

    @Unique private String voxy_hypixel_addon$lastArea = null;
    @Unique private WorldIdentifier voxy_hypixel_addon$cachedAreaId = null;

    /**
     * @author Antigravity
     * @reason Add hypixel area support by returning unique WorldIdentifier instances per area.
     */
    @Overwrite(remap = false)
    public WorldIdentifier voxy$getIdentifier() {
        if (!HypixelManager.isHypixel()) return this.identifier;
        
        String areaId = HypixelManager.getAreaId();
        if (areaId == null) return null;
        
        if (!areaId.equals(voxy_hypixel_addon$lastArea)) {
            WorldIdentifier base = this.identifier;
            if (base == null) return null;
            
            // Create a new identifier instance for the specific Hypixel area
            WorldIdentifier newId = new WorldIdentifier(base.key, base.biomeSeed, base.dimension);
            ((IPerAreaWorldIdentifier)newId).setSubId(areaId);
            
            voxy_hypixel_addon$lastArea = areaId;
            voxy_hypixel_addon$cachedAreaId = newId;
        }
        return voxy_hypixel_addon$cachedAreaId;
    }
}
