package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DispenserBlock.class)
public class DispenserMixin {

    @Inject(at = @At("HEAD"), method = "dispenseFrom", cancellable = true)
    private void onDispense(ServerLevel world, BlockState state, BlockPos pos, CallbackInfo ci) {
        Direction facing = state.getValue(DispenserBlock.FACING);
        BlockPos outputPos = pos.relative(facing);
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, outputPos.getX(), outputPos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.DISPENSER, regionAndScope.getSecond());
        if (value != null && !value) {
            ci.cancel();
        }
    }
}
