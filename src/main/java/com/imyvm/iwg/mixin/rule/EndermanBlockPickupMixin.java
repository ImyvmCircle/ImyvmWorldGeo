package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public class EndermanBlockPickupMixin {

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            cancellable = true)
    private void onRemoveBlock(CallbackInfo ci, @Local(name = "level") Level level,
                               @Local(name = "pos") BlockPos pos) {
        Boolean value = RuleHelper.getEffectiveRuleValueAt(level, pos, RuleKey.ENDERMAN_BLOCK_PICKUP);
        if (Boolean.FALSE.equals(value)) {
            ci.cancel();
        }
    }
}
