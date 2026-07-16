package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonStructureResolver.class)
public class PistonMixin {

    @Final
    @Shadow private Level level;

    @Inject(at = @At("RETURN"), method = "resolve", cancellable = true)
    private void onResolve(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        PistonStructureResolver self = (PistonStructureResolver) (Object) this;
        Direction pushDirection = self.getPushDirection();
        for (BlockPos pos : self.getToPush()) {
            if (isPistonDenied(pos) || isPistonDenied(pos.relative(pushDirection))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos pos : self.getToDestroy()) {
            if (isPistonDenied(pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Unique
    private boolean isPistonDenied(BlockPos pos) {
        return Boolean.FALSE.equals(RuleHelper.getEffectiveRuleValueAt(level, pos, RuleKey.PISTON));
    }
}
