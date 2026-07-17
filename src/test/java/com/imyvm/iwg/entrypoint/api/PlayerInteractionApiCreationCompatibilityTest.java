package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.application.region.RegionFactory;
import com.imyvm.iwg.application.region.Result;
import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.component.GeoShapeType;
import com.imyvm.iwg.inter.api.PlayerInteractionApi;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlayerInteractionApiCreationCompatibilityTest {
    @Test
    public void retainsSupportedCreationDescriptors() throws Exception {
        assertEquals(
            int.class,
            PlayerInteractionApi.class
                .getMethod("createRegion", ServerPlayer.class, String.class, int.class)
                .getReturnType()
        );
        assertEquals(
            Region.class,
            PlayerInteractionApi.class
                .getMethod("createAndGetRegion", ServerPlayer.class, String.class, int.class)
                .getReturnType()
        );
        assertEquals(
            int.class,
            PlayerInteractionApi.class
                .getMethod("addScope", ServerPlayer.class, Region.class, String.class)
                .getReturnType()
        );
        assertEquals(
            Pair.class,
            PlayerInteractionApi.class
                .getMethod("createAndGetRegionScopePair", ServerPlayer.class, Region.class, String.class)
                .getReturnType()
        );
    }

    @Test
    public void retainsBothImplementationCreationFacadeGenerations() throws Exception {
        Class<?> facade = Class.forName("com.imyvm.iwg.application.interaction.CreationApplicationKt");

        assertEquals(
            int.class,
            facade.getMethod(
                "onRegionCreation",
                ServerPlayer.class, String.class, String.class, boolean.class, int.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onRegionCreation",
                ServerPlayer.class, String.class, String.class, boolean.class, boolean.class, int.class
            ).getReturnType()
        );
        assertEquals(
            Region.class,
            facade.getMethod(
                "onTryingRegionCreationWithReturn",
                ServerPlayer.class, String.class, String.class, boolean.class, int.class
            ).getReturnType()
        );
        assertEquals(
            Region.class,
            facade.getMethod(
                "onTryingRegionCreationWithReturn",
                ServerPlayer.class, String.class, String.class, boolean.class, boolean.class, int.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onScopeCreation",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onScopeCreation",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class, boolean.class
            ).getReturnType()
        );
        assertEquals(
            Pair.class,
            facade.getMethod(
                "onTryingScopeCreationWithReturn",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class
            ).getReturnType()
        );
        assertEquals(
            Pair.class,
            facade.getMethod(
                "onTryingScopeCreationWithReturn",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class, boolean.class
            ).getReturnType()
        );

        assertEquals(
            int.class,
            facade.getMethod(
                "onRegionCreation$default",
                ServerPlayer.class, String.class, String.class, boolean.class, int.class,
                int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onRegionCreation$default",
                ServerPlayer.class, String.class, String.class, boolean.class, boolean.class,
                int.class, int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            Region.class,
            facade.getMethod(
                "onTryingRegionCreationWithReturn$default",
                ServerPlayer.class, String.class, String.class, boolean.class, int.class,
                int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            Region.class,
            facade.getMethod(
                "onTryingRegionCreationWithReturn$default",
                ServerPlayer.class, String.class, String.class, boolean.class, boolean.class,
                int.class, int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onScopeCreation$default",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class,
                int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            int.class,
            facade.getMethod(
                "onScopeCreation$default",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class,
                boolean.class, int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            Pair.class,
            facade.getMethod(
                "onTryingScopeCreationWithReturn$default",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class,
                int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            Pair.class,
            facade.getMethod(
                "onTryingScopeCreationWithReturn$default",
                ServerPlayer.class, Region.class, String.class, String.class, boolean.class,
                boolean.class, int.class, Object.class
            ).getReturnType()
        );
    }

    @Test
    public void retainsLegacyFactoryDescriptorsAndDefaultBridges() throws Exception {
        assertEquals(
            Result.class,
            RegionFactory.class.getMethod(
                "createRegion",
                String.class, int.class, ServerPlayer.class, List.class, GeoShapeType.class
            ).getReturnType()
        );
        assertEquals(
            Result.class,
            RegionFactory.class.getMethod(
                "createRegion$default",
                RegionFactory.class, String.class, int.class, ServerPlayer.class, List.class,
                GeoShapeType.class, int.class, Object.class
            ).getReturnType()
        );
        assertEquals(
            Result.class,
            RegionFactory.class.getMethod(
                "createScope",
                String.class, ServerPlayer.class, Identifier.class, BlockPos.class, List.class,
                GeoShapeType.class
            ).getReturnType()
        );
        assertEquals(
            Result.class,
            RegionFactory.class.getMethod(
                "createScope$default",
                RegionFactory.class, String.class, ServerPlayer.class, Identifier.class, BlockPos.class,
                List.class, GeoShapeType.class, int.class, Object.class
            ).getReturnType()
        );
    }
}
