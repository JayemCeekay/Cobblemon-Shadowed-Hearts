package com.jayemceekay.shadowedhearts.mixin.rctapi;

import com.cobblemon.mod.common.item.battle.BagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Pseudo
@Mixin(targets = "com.gitlab.srcmc.rctapi.api.trainer.TrainerBag", remap = false)
public interface RCTTrainerBagAccessor {
    @Accessor("items")
    Map<BagItem, Integer> shadowedhearts$getItemCounts();
}
