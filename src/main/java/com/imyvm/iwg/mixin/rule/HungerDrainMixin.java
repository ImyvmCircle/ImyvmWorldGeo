package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class HungerDrainMixin {

    @Inject(at = @At("HEAD"), method = "causeFoodExhaustion", cancellable = true)
    private void onCauseFoodExhaustion(float exhaustion, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        Level level = self.level();
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockPos pos = self.blockPosition();
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(serverLevel, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.RPG_HUNGER, regionAndScope.getSecond());
        if (value != null && !value) {
            ci.cancel();
        }
    }
}
