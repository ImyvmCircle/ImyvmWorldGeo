package com.imyvm.iwg.mixin.rule;

import com.imyvm.iwg.application.region.rule.helper.RuleHelper;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.infra.RegionDatabase;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasePressurePlateBlock.class)
public class PressurePlateMixin {

    @Inject(at = @At("HEAD"), method = "entityInside", cancellable = true)
    private void onEntityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier applier, boolean bl, CallbackInfo ci) {
        if (world.isClientSide()) return;
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(world, pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Boolean value = RuleHelper.getRuleValue(regionAndScope.getFirst(), RuleKey.PRESSURE_PLATE, regionAndScope.getSecond());
        if (value != null && !value) {
            ci.cancel();
        }
    }
}
