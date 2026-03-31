package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class HungerDrainMixin {

    @Inject(at = @At("HEAD"), method = "causeFoodExhaustion", cancellable = true)
    private void onCauseFoodExhaustion(float exhaustion, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        BlockPos pos = self.blockPosition();
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt((ServerLevel) self.level(), pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.RPG_HUNGER, regionAndScope.getSecond());
        if (value != null && !value) {
            ci.cancel();
        }
    }
}
