package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SnowGolem.class)
public class SnowGolemTrailMixin {

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean onSetSnowBlock(Level world, BlockPos pos, BlockState state) {
        Boolean value = RuleHelper.getEffectiveRuleValueAt(world, pos, RuleKey.SNOW_GOLEM_TRAIL);
        if (Boolean.FALSE.equals(value)) return false;
        return world.setBlockAndUpdate(pos, state);
    }
}
