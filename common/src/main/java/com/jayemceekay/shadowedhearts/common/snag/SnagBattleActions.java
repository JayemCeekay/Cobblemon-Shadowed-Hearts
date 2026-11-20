package com.jayemceekay.shadowedhearts.common.snag;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.BagItemActionResponse;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.HealItemActionResponse;
import com.cobblemon.mod.common.battles.MoveActionResponse;
import com.cobblemon.mod.common.battles.PassActionResponse;
import com.cobblemon.mod.common.battles.ShiftActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.jayemceekay.shadowedhearts.cobblemon.battles.CallActionResponse;
import com.jayemceekay.shadowedhearts.common.shadow.SHAspects;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTItemDebit;

import java.util.List;

public final class SnagBattleActions {
    private SnagBattleActions() {}

    public static void cancelCapturedChoices(BattleActor actor) {
        // A ForcePass response consumes the next entry in this queue. Keep its position.
        cancelInvalidItems(actor.getExpectingPassActions());
        var responses = actor.getResponses();
        cancelInvalidItems(responses);

        var request = actor.getRequest();
        if (request == null || request.getWait() || request.getActive() == null
                || request.getActive().isEmpty() || request.getForceSwitch().contains(true)) return;

        var active = actor.getActivePokemon();
        for (int slot = 0; slot < Math.min(active.size(), responses.size()); slot++) {
            var member = active.get(slot).getBattlePokemon();
            if (member != null && member.getGone() && member.getEffectedPokemon().getAspects().contains(SHAspects.SHADOW)
                    && requiresActivePokemon(responses.get(slot))) {
                responses.set(slot, PassActionResponse.INSTANCE);
            }
        }
    }

    private static boolean requiresActivePokemon(ShowdownActionResponse response) {
        // Trainers can still act, including using an item on a different teammate or forfeiting.
        return response instanceof MoveActionResponse || response instanceof SwitchActionResponse
                || response instanceof ShiftActionResponse || response instanceof DefaultActionResponse
                || response instanceof CallActionResponse || response instanceof HealItemActionResponse;
    }

    private static void cancelInvalidItems(List<ShowdownActionResponse> responses) {
        for (int index = 0; index < responses.size(); index++) {
            if (responses.get(index) instanceof BagItemActionResponse item && item.getTarget().getGone()) {
                RCTItemDebit.refund(item);
                responses.set(index, PassActionResponse.INSTANCE);
            }
        }
    }
}
