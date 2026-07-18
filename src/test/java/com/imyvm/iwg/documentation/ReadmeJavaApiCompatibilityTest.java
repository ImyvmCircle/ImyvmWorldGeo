package com.imyvm.iwg.documentation;

import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.ScopeOwnershipEntry;
import com.imyvm.iwg.domain.TimedEffect;
import com.imyvm.iwg.domain.TimedEffectOverlay;
import com.imyvm.iwg.domain.component.EffectKey;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.PermissionKey;
import com.imyvm.iwg.inter.api.PlayerInteractionApi;
import com.imyvm.iwg.inter.api.RegionDataApi;
import com.imyvm.iwg.inter.api.SettingAddResult;
import kotlin.Pair;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ReadmeJavaApiCompatibilityTest {
    @Test
    public void readmeJavaEntryPointsHaveOrdinaryDescriptors() throws Exception {
        assertEquals(
            SettingAddResult.class,
            PlayerInteractionApi.class.getMethod(
                "addScopePermission",
                ServerPlayer.class,
                Region.class,
                GeoScope.class,
                com.imyvm.iwg.domain.component.PermissionKeyLike.class,
                boolean.class,
                UUID.class
            ).getReturnType()
        );
        assertEquals(
            Long.class,
            RegionDataApi.class.getMethod("parseAssignedScopeIdRaw", String.class).getReturnType()
        );
        assertEquals(
            Pair.class,
            RegionDataApi.class.getMethod("getScopeByAssignedIdRaw", long.class).getReturnType()
        );
        assertEquals(
            TimedEffectOverlay.class,
            RegionDataApi.class.getMethod(
                "createTimedEffectOverlayRaw",
                String.class,
                long.class,
                List.class,
                long.class,
                long.class,
                int.class,
                String.class
            ).getReturnType()
        );
    }

    @SuppressWarnings("unused")
    private static void compileRepresentativeJavaCalls(
        ServerPlayer player,
        Region region,
        GeoScope scope,
        UUID targetPlayer,
        String scopeIdText,
        long startsAt,
        long expiresAt
    ) {
        SettingAddResult result = PlayerInteractionApi.INSTANCE.addScopePermission(
            player, region, scope, PermissionKey.BUILD, false, targetPlayer
        );

        Long scopeIdRaw = RegionDataApi.INSTANCE.parseAssignedScopeIdRaw(scopeIdText);
        if (scopeIdRaw == null) return;
        Pair<Region, GeoScope> resolved =
            RegionDataApi.INSTANCE.getScopeByAssignedIdRaw(scopeIdRaw.longValue());
        String persistedText =
            RegionDataApi.INSTANCE.formatAssignedScopeIdRaw(scopeIdRaw.longValue());
        List<ScopeOwnershipEntry> history =
            RegionDataApi.INSTANCE.getAssignedScopeOwnershipHistoryRaw(scopeIdRaw.longValue());

        TimedEffectOverlay overlay = RegionDataApi.INSTANCE.createTimedEffectOverlayRaw(
            "event:festival",
            scopeIdRaw.longValue(),
            List.of(new TimedEffect(EffectKey.SPEED, 1)),
            startsAt,
            expiresAt,
            0,
            "documentation"
        );
        RegionDataApi.INSTANCE.applyTimedEffectOverlay(overlay);
        Map<EffectKey, Integer> effective =
            RegionDataApi.INSTANCE.queryOverlayRaw(scopeIdRaw.longValue());
        RegionDataApi.INSTANCE.clearTimedEffectOverlayRaw(scopeIdRaw.longValue(), overlay.getOverlayId());
        RegionDataApi.INSTANCE.queryActiveOverlaysRaw(scopeIdRaw.longValue());

        List.of(result, resolved, persistedText, history, effective);
    }
}
