package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.EffectKey;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.PermissionKey;
import com.imyvm.iwg.domain.component.RuleKey;
import com.imyvm.iwg.inter.api.PlayerInteractionApi;
import com.imyvm.iwg.inter.api.SettingAddResult;
import com.imyvm.iwg.inter.api.SettingRemoveResult;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class PlayerInteractionApiSettingCompatibilityTest {
    @Test
    public void exposesJavaFriendlyTypedOverloadsAndResults() throws Exception {
        assertEquals(
            void.class,
            PlayerInteractionApi.class
                .getMethod("addSettingRegion", ServerPlayer.class, Region.class, String.class, String.class, String.class)
                .getReturnType()
        );
        assertEquals(
            void.class,
            PlayerInteractionApi.class
                .getMethod("removeSettingScope", ServerPlayer.class, Region.class, String.class, String.class, String.class)
                .getReturnType()
        );
        assertEquals(
            SettingAddResult.class,
            PlayerInteractionApi.class
                .getMethod("addRegionPermission", ServerPlayer.class, Region.class, com.imyvm.iwg.domain.component.PermissionKeyLike.class, boolean.class)
                .getReturnType()
        );
        assertEquals(
            SettingAddResult.class,
            PlayerInteractionApi.class
                .getMethod("addRegionPermission", ServerPlayer.class, Region.class, com.imyvm.iwg.domain.component.PermissionKeyLike.class, boolean.class, UUID.class)
                .getReturnType()
        );
        assertEquals(
            SettingRemoveResult.class,
            PlayerInteractionApi.class
                .getMethod("removeScopeEffect", ServerPlayer.class, Region.class, GeoScope.class, EffectKey.class, UUID.class)
                .getReturnType()
        );
    }

    @SuppressWarnings("unused")
    private static void compileRepresentativeCalls(
        ServerPlayer executor,
        Region region,
        GeoScope scope,
        UUID targetPlayer
    ) {
        SettingAddResult global = PlayerInteractionApi.INSTANCE.addRegionPermission(
            executor, region, PermissionKey.BUILD, true
        );
        SettingAddResult personal = PlayerInteractionApi.INSTANCE.addRegionPermission(
            executor, region, PermissionKey.BUILD, true, targetPlayer
        );
        SettingRemoveResult effect = PlayerInteractionApi.INSTANCE.removeScopeEffect(
            executor, region, scope, EffectKey.SPEED, targetPlayer
        );
        SettingAddResult rule = PlayerInteractionApi.INSTANCE.addScopeRule(
            executor, region, scope, RuleKey.PISTON, false
        );
    }
}
