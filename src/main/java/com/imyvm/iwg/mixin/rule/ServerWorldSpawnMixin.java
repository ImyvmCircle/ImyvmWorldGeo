package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldSpawnMixin {

    @Inject(at = @At("HEAD"), method = "spawnEntity", cancellable = true)
    private void onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof MobEntity mob)) return;
        if (mob.getType().getSpawnGroup() != SpawnGroup.MONSTER) return;

        ServerWorld world = (ServerWorld) (Object) this;
        BlockPos pos = entity.getBlockPos();

        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;

        Region region = regionAndScope.getFirst();
        GeoScope scope = regionAndScope.getSecond();

        RuleKey key;
        if (entity instanceof PhantomEntity) {
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
