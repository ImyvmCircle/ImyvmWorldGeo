package com.imyvm.iwg.mixin.permission;

import com.imyvm.iwg.application.region.permission.helper.PermissionDenialSource;
import com.imyvm.iwg.application.region.permission.helper.PermissionHelperKt;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.PermissionKey;
import com.imyvm.iwg.domain.component.SubSpace;
import com.imyvm.iwg.infra.RegionDatabase;
import com.imyvm.iwg.infra.config.PermissionConfig;
import com.imyvm.iwg.util.text.Translator;
import kotlin.Triple;
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
        Triple<Region, GeoScope, SubSpace> resolved = RegionDatabase.INSTANCE.getRegionScopeSubSpaceAt(
                ((ItemEntity) (Object) this).level(), pos.getX(), pos.getZ());
        if (resolved == null) return;
        Region region = resolved.getFirst();
        GeoScope scope = resolved.getSecond();
        SubSpace subSpace = resolved.getThird();
        PermissionDenialSource denial = subSpace == null
                ? PermissionHelperKt.getScopePermissionDenialSource(
                        region, scope, player.getUUID(), PermissionKey.RPG_ITEM_PICKUP,
                        PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP.getValue())
                : PermissionHelperKt.getSubSpacePermissionDenialSource(
                        region, scope, subSpace, player.getUUID(), PermissionKey.RPG_ITEM_PICKUP,
                        PermissionConfig.PERMISSION_DEFAULT_RPG_ITEM_PICKUP.getValue());
        if (denial == null) return;
        String ctx = subSpace == null
                ? PermissionHelperKt.buildScopePermissionDenialContext(region, scope, denial)
                : PermissionHelperKt.buildSubSpacePermissionDenialContext(region, scope, subSpace, denial);
        player.sendSystemMessage(Translator.INSTANCE.tr("setting.permission.item_pickup", ctx));
        ci.cancel();
    }
}
