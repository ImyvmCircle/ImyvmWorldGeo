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
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public class NaturalRegenMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void onHeal(Player player, float amount) {
        Level level = player.level();
        if (level instanceof ServerLevel serverLevel) {
            BlockPos pos = player.blockPosition();
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(serverLevel, pos.getX(), pos.getZ());
            if (regionAndScope != null) {
                Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.RPG_NATURAL_REGEN, regionAndScope.getSecond());
                if (value != null && !value) return;
            }
        }
        player.heal(amount);
    }
}
