package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.ScopeOwnershipEntry;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.domain.component.GeoShape;
import com.imyvm.iwg.domain.component.ScopeId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.junit.Test;
import kotlin.jvm.internal.DefaultConstructorMarker;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RegionAggregateCompatibilityTest {
    @Test
    public void retainsRegionConstructionPropertyAndMutationDescriptors() throws Exception {
        Region.class.getConstructor(String.class, int.class, List.class, List.class, boolean.class, Map.class);
        assertEquals(void.class, Region.class.getMethod("setName", String.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("setShowOnDynmap", boolean.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("setGeometryScope", List.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("setOwnershipHistoryByScope", Map.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("setSettings", List.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("addScope", GeoScope.class).getReturnType());
        assertEquals(int.class, Region.class.getMethod("removeScope", GeoScope.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("restoreScope", int.class, GeoScope.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("renameScope", GeoScope.class, String.class).getReturnType());
        assertEquals(void.class, Region.class.getMethod("recordScopeOwnership", ScopeOwnershipEntry.class).getReturnType());
    }

    @Test
    public void retainsGeoScopeConstructionAndSettingsDescriptor() throws Exception {
        GeoScope.class.getConstructor(
            String.class,
            Identifier.class,
            BlockPos.class,
            boolean.class,
            GeoShape.class,
            List.class,
            boolean.class,
            long.class,
            DefaultConstructorMarker.class
        );
        assertEquals(void.class, GeoScope.class.getMethod("setSettings", List.class).getReturnType());
    }
}
