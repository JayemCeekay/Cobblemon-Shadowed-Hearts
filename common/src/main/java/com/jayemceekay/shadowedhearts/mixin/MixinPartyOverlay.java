package com.jayemceekay.shadowedhearts.mixin;


import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.jayemceekay.shadowedhearts.Shadowedhearts;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowAspectUtil;
import com.jayemceekay.shadowedhearts.common.shadow.ShadowHeartGauge;
import com.jayemceekay.shadowedhearts.config.HeartGaugeConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = PartyOverlay.class, remap = false)
public class MixinPartyOverlay {

    private static final String BLIT = "Lcom/cobblemon/mod/common/api/gui/GuiUtilsKt;blitk$default("
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/ResourceLocation;"
            + "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;"
            + "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;"
            + "Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;Ljava/lang/Number;"
            + "ZFILjava/lang/Object;)V";

    ResourceLocation SHADOW_FRAME = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/party_slot.png");

    ResourceLocation SHADOW_FRAME_ACTIVE = ResourceLocation.fromNamespaceAndPath(Shadowedhearts.MOD_ID, "textures/gui/party_slot_active.png");

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = BLIT, ordinal = 1), index = 1)
    public ResourceLocation shadowedhearts$replacePortraitFrame(ResourceLocation resourceLocation, @Local(name = "pokemon") Pokemon pokemon, @Local(name = "index") int index) {
        if(ShadowAspectUtil.hasShadowAspect(pokemon) && !pokemon.isFainted()) {
            return CobblemonClient.INSTANCE.getStorage().getSelectedSlot() == index ? SHADOW_FRAME_ACTIVE : SHADOW_FRAME;
        }
        return resourceLocation;
    }

    // The first blit after the EXP calculation draws the bar, including during a level-up animation.
    @ModifyArgs(method = "render",
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/pokemon/Pokemon;getExperience()I", ordinal = 0)),
            at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void shadowedhearts$replaceExperienceBar(Args args, @Local(name = "pokemon") Pokemon pokemon,
            @Local(name = "indexY") int slotY, @Local(name = "barHeightMax") int barHeight) {
        if (!ShadowAspectUtil.hasShadowAspect(pokemon)) return;

        int meter = ShadowHeartGauge.getHeartGaugeMeter(pokemon);
        int filled = (int) Math.ceilDiv((long) meter * barHeight, HeartGaugeConfig.getMax(pokemon));
        int empty = barHeight - filled;
        args.set(3, slotY + 5 + empty); // y
        args.set(4, filled);            // height
        args.set(7, empty);             // vOffset
        args.set(9, barHeight);         // textureHeight, including when the gauge is empty
        // Match the summary screen's Heart Gauge purple (#8100FF).
        args.set(11, 129 / 255f);
        args.set(12, 0f);
        args.set(13, 1f);
    }

}
