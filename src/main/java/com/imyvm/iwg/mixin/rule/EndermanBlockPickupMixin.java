package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal")
public class EndermanBlockPickupMixin {

    @Shadow
    private EndermanEntity enderman;

    @Inject(at = @At("HEAD"), method = "canStart", cancellable = true)
    private void onCanStart(CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = enderman.getBlockPos();
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(enderman.getWorld(), pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.ENDERMAN_BLOCK_PICKUP, regionAndScope.getSecond());
        if (value != null && !value) {
            cir.setReturnValue(false);
        }
    }
}
