package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.PositionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(targets = "net.minecraft.block.entity.SculkCatalystBlockEntity$Listener")
public class SculkSpreadMixin {

    @Shadow
    private PositionSource positionSource;

    @Inject(at = @At("HEAD"), method = "listen", cancellable = true)
    private void onListen(ServerWorld world, RegistryEntry<GameEvent> event, GameEvent.Emitter emitter, Vec3d emitterPos, CallbackInfoReturnable<Boolean> cir) {
        Optional<Vec3d> posOpt = positionSource.getPos(world);
        if (posOpt.isEmpty()) return;
        BlockPos pos = BlockPos.ofFloored(posOpt.get());
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.SCULK_SPREAD, regionAndScope.getSecond());
        if (value != null && !value) {
            cir.setReturnValue(false);
        }
    }
}
