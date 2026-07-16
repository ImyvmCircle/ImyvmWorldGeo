package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkBlock;
import net.minecraft.world.level.block.SculkSpreader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SculkBlock.class)
public class SculkGrowthMixin {

    @Inject(method = "attemptUseCharge", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            cancellable = true)
    private void onPlaceGrowth(SculkSpreader.ChargeCursor cursor, LevelAccessor world, BlockPos originPos,
                               RandomSource random, SculkSpreader spreader, boolean spreadVein,
                               CallbackInfoReturnable<Integer> cir, @Local(name = "charge") int charge,
                               @Local(name = "growthPlacement") BlockPos growthPlacement) {
        if (!(world instanceof Level level)) return;
        Boolean value = RuleHelper.getEffectiveRuleValueAt(level, growthPlacement, RuleKey.SCULK_SPREAD);
        if (Boolean.FALSE.equals(value)) {
            cir.setReturnValue(charge);
        }
    }
}
