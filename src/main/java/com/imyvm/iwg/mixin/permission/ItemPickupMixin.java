package com.imyvm.iwg.mixin.permission;

import com.imyvm.iwg.application.region.permission.helper.PermissionDenialSource;
import com.imyvm.iwg.application.region.permission.helper.PermissionHelperKt;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.PermissionKey;
import com.imyvm.iwg.infra.RegionDatabase;
import com.imyvm.iwg.infra.config.PermissionConfig;
import com.imyvm.iwg.util.text.Translator;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemPickupMixin {

    @Inject(at = @At("HEAD"), method = "playerTouch", cancellable = true)
    private void onPlayerTouch(Player player, CallbackInfo ci) {
        BlockPos pos = ((ItemEntity) (Object) this).blockPosition();
        Pair<Region, GeoScope> regionAndScope = RegionDatabase.INSTANCE.getRegionAndScopeAt(
                ((ItemEntity) (Object) this).level(), pos.getX(), pos.getZ());
        if (regionAndScope == null) return;
        Region region = regionAndScope.getFirst();
        GeoScope scope = regionAndScope.getSecond();
        PermissionDenialSource denial = PermissionHelperKt.getPermissionDenialSource(
                region, player.getUUID(), PermissionKey.RPG_ITEM_PICKUP, scope,
                PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP.getValue());
        if (denial == null) return;
        String ctx = PermissionHelperKt.buildPermissionDenialContext(region, scope, denial);
        player.sendSystemMessage(Translator.INSTANCE.tr("setting.permission.item_pickup", ctx));
        ci.cancel();
    }
}
