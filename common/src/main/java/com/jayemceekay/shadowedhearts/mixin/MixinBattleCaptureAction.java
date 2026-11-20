package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleCaptureAction;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.common.snag.SnagBattleActions;
import com.jayemceekay.shadowedhearts.common.snag.SnagCaptureProvenance;
import com.jayemceekay.shadowedhearts.integration.mega_showdown.MegaShowdownBridgeHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleCaptureAction.class, remap = false)
public class MixinBattleCaptureAction {

    @Inject(method = "attach", at = @At("HEAD"))
    private void shadowedhearts$rememberSnagOrigin(CallbackInfo ci) {
        SnagCaptureProvenance.recordAttempt((BattleCaptureAction) (Object) this);
    }

    @WrapOperation(method = "attach$lambda$3", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/PokemonBattle;writeShowdownAction([Ljava/lang/String;)V"))
    private static void shadowedhearts$sendCapture(PokemonBattle battle, String[] messages, Operation<Void> original,
                                                   @Local(argsOnly = true) BattleCaptureAction action) {
        var member = action.getTargetPokemon().getBattlePokemon();
        if (member == null || !member.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)) {
            original.call(battle, messages);
            return;
        }
        // Cobblemon has marked the member gone. A final capture can end the battle before another dispatch.
        for (var actor : battle.getActors()) {
            SnagBattleActions.cancelCapturedChoices(actor);
        }
        original.call(battle, messages);
        MegaShowdownBridgeHolder.INSTANCE.revertMegaShowdownAspects(member.getEffectedPokemon());
    }
}
