package com.imyvm.iwg.mixin.stats;

import com.imyvm.iwg.application.event.PlayerStatsRecorderKt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemPlaceMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) return;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos clickedPos = context.getClickedPos();
        BlockPos placedPos = PlayerStatsRecorderKt.resolvePlacedBlockTarget(
                clickedPos,
                context.getClickedFace(),
                context.getLevel().getBlockState(clickedPos),
                context.getLevel().getBlockState(clickedPos.relative(context.getClickedFace())),
                ((BlockItem) (Object) this).getBlock()
        );
        String objectId = BuiltInRegistries.BLOCK.getKey(((BlockItem) (Object) this).getBlock()).toString();
        PlayerStatsRecorderKt.recordSuccessfulBlockPlacement(player, context.getLevel(), placedPos, objectId);
    }
}
