package com.jayemceekay.shadowedhearts.mixin.rctapi;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.item.battle.BagItem;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerBag;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTItemDebit;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.gitlab.srcmc.rctapi.api.ai.utils.ResponseBuilder", remap = false)
public abstract class MixinRCTItemTargets {
    // RCT API 0.16.2-beta evaluates this predicate for each bag item and roster member.
    @Inject(method = "lambda$selectItemUsages$5(Lcom/cobblemon/mod/common/item/battle/BagItem;"
            + "Lcom/cobblemon/mod/common/battles/ActiveBattlePokemon;"
            + "Lcom/cobblemon/mod/common/battles/pokemon/BattlePokemon;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void shadowedhearts$excludeGoneItemTarget(BagItem item, ActiveBattlePokemon active,
                                                            BattlePokemon target, CallbackInfoReturnable<Boolean> cir) {
        if (target.getGone()) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "lambda$suggestItems$11", at = @At(value = "INVOKE",
            target = "Lcom/gitlab/srcmc/rctapi/api/trainer/TrainerBag;use(Lcom/cobblemon/mod/common/item/battle/BagItem;)Lcom/cobblemon/mod/common/item/battle/BagItem;"), require = 0)
    private BagItem shadowedhearts$recordItemDebit(TrainerBag bag, BagItem item, Operation<BagItem> use,
                                                  @Share("rctItemRefund") LocalRef<Runnable> refund) {
        int stock = bag.getQuanity(item);
        BagItem result = use.call(bag, item);
        int spent = stock - bag.getQuanity(item);
        if (spent > 0) {
            // TrainerBag.add takes a registry ID and overwrites the current quantity.
            var counts = ((RCTTrainerBagAccessor) bag).shadowedhearts$getItemCounts();
            refund.set(() -> counts.merge(item, spent, Integer::sum));
        }
        return result;
    }

    @WrapOperation(method = "lambda$suggestItems$11", at = @At(value = "INVOKE",
            target = "Lcom/cobblemon/mod/common/api/battles/model/actor/BattleActor;forceChoose(Lcom/cobblemon/mod/common/battles/ShowdownActionResponse;)V"), require = 0)
    private void shadowedhearts$attachItemDebit(BattleActor actor, ShowdownActionResponse action, Operation<Void> choose,
                                               @Share("rctItemRefund") LocalRef<Runnable> refund) {
        if (action instanceof RCTItemDebit debit) debit.shadowedhearts$setRefund(refund.get());
        choose.call(actor, action);
    }
}
