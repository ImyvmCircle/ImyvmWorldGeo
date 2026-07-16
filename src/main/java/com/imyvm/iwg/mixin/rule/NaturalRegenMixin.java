package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public class NaturalRegenMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void onHeal(ServerPlayer player, float amount) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();
        Boolean value = RuleHelper.getEffectiveRuleValueAt(level, pos, RuleKey.RPG_NATURAL_REGEN);
        if (Boolean.FALSE.equals(value)) return;
        player.heal(amount);
    }
}
