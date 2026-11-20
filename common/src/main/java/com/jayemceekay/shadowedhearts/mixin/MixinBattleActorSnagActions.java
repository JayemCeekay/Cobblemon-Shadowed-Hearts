package com.jayemceekay.shadowedhearts.mixin;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.jayemceekay.shadowedhearts.common.snag.SnagBattleActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BattleActor.class, remap = false)
public abstract class MixinBattleActorSnagActions {
    @Inject(method = "writeShowdownResponse()V", at = @At("HEAD"))
    private void shadowedhearts$checkPendingSnagActions(CallbackInfo ci) {
        SnagBattleActions.cancelCapturedChoices((BattleActor) (Object) this);
    }
}
