package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.inter.api.PlayerInteractionApi;
import com.imyvm.iwg.inter.api.RegionDeleteResult;
import com.imyvm.iwg.inter.api.ScopeDeleteResult;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerInteractionApiDeleteCompatibilityTest {
    @Test
    public void retainsReleasedVoidDescriptorsAndExposesObservableReplacements() throws Exception {
        assertEquals(
            void.class,
            PlayerInteractionApi.class
                .getMethod("deleteRegion", ServerPlayer.class, Region.class)
                .getReturnType()
        );
        assertEquals(
            void.class,
            PlayerInteractionApi.class
                .getMethod("deleteScope", ServerPlayer.class, Region.class, String.class)
                .getReturnType()
        );
        assertEquals(
            RegionDeleteResult.class,
            PlayerInteractionApi.class
                .getMethod("deleteRegionWithResult", ServerPlayer.class, Region.class)
                .getReturnType()
        );
        assertEquals(
            ScopeDeleteResult.class,
            PlayerInteractionApi.class
                .getMethod("deleteScopeWithResult", ServerPlayer.class, Region.class, GeoScope.class)
                .getReturnType()
        );
    }
}
