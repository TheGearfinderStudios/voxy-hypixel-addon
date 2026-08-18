package me.cortex.voxy.addon.hypixel.mixin;

import com.google.gson.JsonObject;
import me.cortex.voxy.addon.hypixel.HypixelManager;
import me.cortex.voxy.addon.hypixel.access.IFlashbackHypixelMeta;
import me.cortex.voxy.common.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.moulberry.flashback.record.FlashbackMeta", remap = false)
public class MixinFlashbackMeta implements IFlashbackHypixelMeta {
    @Unique private String voxy_hypixel_addon$area;
    @Unique private String voxy_hypixel_addon$rawArea;

    @Override
    public void voxy_hypixel_addon$setHypixelArea(String area) {
        this.voxy_hypixel_addon$area = area;
    }

    @Override
    public String voxy_hypixel_addon$getHypixelArea() {
        return this.voxy_hypixel_addon$area;
    }

    @Override
    public void voxy_hypixel_addon$setHypixelRawArea(String rawArea) {
        this.voxy_hypixel_addon$rawArea = rawArea;
    }

    @Override
    public String voxy_hypixel_addon$getHypixelRawArea() {
        return this.voxy_hypixel_addon$rawArea;
    }

    @Inject(method = "toJson", at = @At("RETURN"), require = 0)
    private void voxy_hypixel_addon$saveHypixelMeta(CallbackInfoReturnable<JsonObject> cir) {
        JsonObject val = cir.getReturnValue();
        if (val != null) {
            String area = this.voxy_hypixel_addon$area != null ? this.voxy_hypixel_addon$area : HypixelManager.getAreaId();
            String rawArea = this.voxy_hypixel_addon$rawArea != null ? this.voxy_hypixel_addon$rawArea : HypixelManager.getRawAreaId();
            if (area != null) {
                val.addProperty("voxy_hypixel_area", area);
                Logger.info("[Voxy-Addon-Flashback] Saved Hypixel area to Flashback meta.json -> Area: " + area);
            }
            if (rawArea != null) {
                val.addProperty("voxy_hypixel_raw_area", rawArea);
            }
        }
    }

    @Inject(method = "fromJson", at = @At("RETURN"), require = 0)
    private static void voxy_hypixel_addon$loadHypixelMeta(JsonObject meta, CallbackInfoReturnable<?> cir) {
        Object val = cir.getReturnValue();
        if (val instanceof IFlashbackHypixelMeta hypixelMeta && meta != null) {
            if (meta.has("voxy_hypixel_area")) {
                String area = meta.get("voxy_hypixel_area").getAsString();
                hypixelMeta.voxy_hypixel_addon$setHypixelArea(area);
                Logger.info("[Voxy-Addon-Flashback] Loaded Hypixel area from meta.json -> Area: " + area);
            }
            if (meta.has("voxy_hypixel_raw_area")) {
                String rawArea = meta.get("voxy_hypixel_raw_area").getAsString();
                hypixelMeta.voxy_hypixel_addon$setHypixelRawArea(rawArea);
            }
        }
    }
}
