package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.entity.TntEntity;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public class ExplosionBlockProtectionMixin {

    @Shadow
    private World world;

    @Inject(at = @At("HEAD"), method = "affectWorld")
    private void filterProtectedRegionBlocks(boolean createParticles, CallbackInfo ci) {
        Explosion self = (Explosion) (Object) this;
        if (!(self.getEntity() instanceof TntEntity)) return;

        self.getAffectedBlocks().removeIf(pos -> {
            Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
            if (regionAndScope == null) return false;
            Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.TNT_BLOCK_PROTECTION, regionAndScope.getSecond());
            return value != null && value;
        });
    }
}
