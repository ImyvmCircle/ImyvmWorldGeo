package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.component.RuleKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerExplosion.class)
public class ExplosionBlockProtectionMixin {

    @Final
    @Shadow
    private ServerLevel level;

    @Final
    @Shadow
    private Entity source;

    @Final
    @Shadow
    private DamageSource damageSource;

    @Inject(at = @At("HEAD"), method = "interactWithBlocks")
    private void filterProtectedRegionBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        boolean covered = source instanceof PrimedTnt
                || source instanceof MinecartTNT
                || source instanceof EndCrystal
                || source instanceof WitherBoss
                || source instanceof WitherSkull
                || (damageSource != null && damageSource.is(DamageTypes.BAD_RESPAWN_POINT));
        if (!covered) return;

        blocks.removeIf(pos -> Boolean.TRUE.equals(
                RuleHelper.getEffectiveRuleValueAt(level, pos, RuleKey.TNT_BLOCK_PROTECTION)
        ));
    }
}
