package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonStructureResolver.class)
public class PistonMixin {

    @Shadow private Level level;

    @Inject(at = @At("RETURN"), method = "resolve", cancellable = true)
    private void onResolve(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        PistonStructureResolver self = (PistonStructureResolver) (Object) this;
        for (BlockPos pos : self.getToPush()) {
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(level, pos.getX(), pos.getZ());
            if (regionAndScope != null) {
                Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.PISTON, regionAndScope.getSecond());
                if (value != null && !value) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
        for (BlockPos pos : self.getToDestroy()) {
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(level, pos.getX(), pos.getZ());
            if (regionAndScope != null) {
                Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.PISTON, regionAndScope.getSecond());
                if (value != null && !value) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }
}
