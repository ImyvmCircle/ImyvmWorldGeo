package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.SculkVeinBlock$SculkVeinSpreaderConfig")
public class SculkSpreadMixin {

    @Inject(method = "stateCanBeReplaced", at = @At("RETURN"), cancellable = true)
    private void onStateCanBeReplaced(BlockGetter world, BlockPos sourcePos, BlockPos placementPos,
                                      Direction placementDirection, BlockState existingState,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(world instanceof Level level)) return;
        Boolean value = RuleHelper.getEffectiveRuleValueAt(level, placementPos, RuleKey.SCULK_SPREAD);
        if (Boolean.FALSE.equals(value)) {
            cir.setReturnValue(false);
        }
    }
}
