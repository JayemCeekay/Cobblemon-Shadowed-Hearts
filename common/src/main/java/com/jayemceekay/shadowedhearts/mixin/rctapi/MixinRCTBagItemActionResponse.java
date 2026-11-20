package com.jayemceekay.shadowedhearts.mixin.rctapi;

import com.cobblemon.mod.common.battles.BagItemActionResponse;
import com.jayemceekay.shadowedhearts.integration.rctmod.RCTItemDebit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BagItemActionResponse.class, remap = false)
public abstract class MixinRCTBagItemActionResponse implements RCTItemDebit {
    @Unique private Runnable shadowedhearts$refund;

    @Override
    public void shadowedhearts$setRefund(Runnable refund) {
        shadowedhearts$refund = refund;
    }

    @Override
    public boolean shadowedhearts$refundOnce() {
        Runnable refund = shadowedhearts$refund;
        if (refund == null) return false;
        shadowedhearts$refund = null;
        refund.run();
        return true;
    }
}
