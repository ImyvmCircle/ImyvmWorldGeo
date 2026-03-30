package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerWorldSpawnMixin {

    @Inject(at = @At("HEAD"), method = "addFreshEntity", cancellable = true)
    private void onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Mob mob)) return;
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;

        ServerLevel world = (ServerLevel) (Object) this;
        BlockPos pos = entity.blockPosition();

        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;

        Region region = regionAndScope.getFirst();
        GeoScope scope = regionAndScope.getSecond();

        RuleKey key;
        if (entity instanceof Phantom) {
            key = RuleKey.SPAWN_PHANTOMS;
        } else {
            key = RuleKey.SPAWN_MONSTERS;
        }

        Boolean value = RuleHelper.getRuleValue(region, key, scope);
        if (value != null && !value) {
            cir.setReturnValue(false);
        }
    }
}
