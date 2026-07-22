package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class FireSpreadMixin {

    @Inject(at = @At("HEAD"), method = "checkBurnOut", cancellable = true)
    private void onCheckBurnOut(Level world, BlockPos pos, int spreadFactor, RandomSource random, int age, CallbackInfo ci) {
        Boolean value = RuleHelper.getEffectiveRuleValueAt(world, pos, RuleKey.RPG_FIRE_SPREAD);
        if (Boolean.FALSE.equals(value)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            ordinal = 1), cancellable = true)
    private void onPlaceNewFire(BlockState state, ServerLevel world, BlockPos pos, RandomSource random, CallbackInfo ci) {
        Boolean value = RuleHelper.getEffectiveRuleValueAt(world, pos, RuleKey.RPG_FIRE_SPREAD);
        if (Boolean.FALSE.equals(value)) ci.cancel();
    }
}
