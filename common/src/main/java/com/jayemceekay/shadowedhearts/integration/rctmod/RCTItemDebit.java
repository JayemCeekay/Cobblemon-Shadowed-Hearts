package com.jayemceekay.shadowedhearts.integration.rctmod;

import com.cobblemon.mod.common.battles.ShowdownActionResponse;

/** A trainer bag debit belongs to the exact queued action that incurred it. */
public interface RCTItemDebit {
    void shadowedhearts$setRefund(Runnable refund);

    boolean shadowedhearts$refundOnce();

    static boolean refund(ShowdownActionResponse response) {
        return response instanceof RCTItemDebit debit && debit.shadowedhearts$refundOnce();
    }
}
