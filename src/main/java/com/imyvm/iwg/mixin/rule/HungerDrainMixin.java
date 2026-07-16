package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
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
        Boolean value = RuleHelper.getEffectiveRuleValueAt(serverLevel, pos, RuleKey.RPG_HUNGER);
        if (Boolean.FALSE.equals(value)) {
            ci.cancel();
        }
    }
}
