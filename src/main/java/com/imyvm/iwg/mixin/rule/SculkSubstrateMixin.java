package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.SculkVeinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

@Mixin(SculkVeinBlock.class)
public class SculkSubstrateMixin {

    @Inject(method = "attemptPlaceSculk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),
            cancellable = true)
    private void onPlaceSculk(SculkSpreader spreader, LevelAccessor world, BlockPos pos, RandomSource random,
                              CallbackInfoReturnable<Boolean> cir,
                              @Local(name = "supportPos") BlockPos supportPos) {
        if (!(world instanceof Level level)) return;
        Boolean value = RuleHelper.getEffectiveRuleValueAt(level, supportPos, RuleKey.SCULK_SPREAD);
        if (Boolean.FALSE.equals(value)) {
            cir.setReturnValue(false);
        }
    }
}
