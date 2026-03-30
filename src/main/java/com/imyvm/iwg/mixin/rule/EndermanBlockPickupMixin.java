package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public class EndermanBlockPickupMixin {

    @Shadow
    private EnderMan enderman;

    @Inject(at = @At("HEAD"), method = "canUse", cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = enderman.blockPosition();
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(enderman.level(), pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.ENDERMAN_BLOCK_PICKUP, regionAndScope.getSecond());
        if (value != null && !value) {
            cir.setReturnValue(false);
        }
    }
}
