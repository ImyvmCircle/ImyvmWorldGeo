package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(targets = "net.minecraft.world.level.block.entity.SculkCatalystBlockEntity$CatalystListener")
public class SculkSpreadMixin {

    @Shadow
    private PositionSource positionSource;

    @Inject(at = @At("HEAD"), method = "handleGameEvent", cancellable = true)
    private void onHandleGameEvent(ServerLevel world, Holder<GameEvent> event, GameEvent.Context context, Vec3 emitterPos, CallbackInfoReturnable<Boolean> cir) {
        Optional<Vec3> posOpt = positionSource.getPosition(world);
        if (posOpt.isEmpty()) return;
        BlockPos pos = BlockPos.containing(posOpt.get());
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.SCULK_SPREAD, regionAndScope.getSecond());
        if (value != null && !value) {
            cir.setReturnValue(false);
        }
    }
}
