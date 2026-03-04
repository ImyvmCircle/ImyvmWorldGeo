package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonHandler.class)
public class PistonMixin {

    @Shadow private World world;
    @Shadow private List<BlockPos> movedBlocks;
    @Shadow private List<BlockPos> brokenBlocks;

    @Inject(at = @At("RETURN"), method = "calculatePush", cancellable = true)
    private void onCalculatePush(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        for (BlockPos pos : movedBlocks) {
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
            if (regionAndScope != null) {
                Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.PISTON, regionAndScope.getSecond());
                if (value != null && !value) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
        for (BlockPos pos : brokenBlocks) {
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
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
