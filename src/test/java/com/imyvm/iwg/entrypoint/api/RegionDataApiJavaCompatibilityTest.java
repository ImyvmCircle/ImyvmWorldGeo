package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.domain.Region;
import com.imyvm.iwg.domain.ScopeOwnershipEntry;
import com.imyvm.iwg.domain.TimedEffect;
import com.imyvm.iwg.domain.TimedEffectOverlay;
import com.imyvm.iwg.domain.component.EffectKey;
import com.imyvm.iwg.domain.component.GeoScope;
import com.imyvm.iwg.inter.api.RegionDataApi;
import kotlin.Pair;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RegionDataApiJavaCompatibilityTest {
    @Test
    public void exposesOrdinaryJavaRawMethods() throws Exception {
        assertEquals(Long.class, RegionDataApi.class.getMethod("parseAssignedScopeIdRaw", String.class).getReturnType());
        assertEquals(String.class, RegionDataApi.class.getMethod("formatAssignedScopeIdRaw", long.class).getReturnType());
        assertEquals(Pair.class, RegionDataApi.class.getMethod("getScopeByAssignedIdRaw", long.class).getReturnType());
        assertEquals(Long.class, RegionDataApi.class.getMethod("getAssignedScopeIdRawOrNull", GeoScope.class).getReturnType());
        assertEquals(List.class, RegionDataApi.class.getMethod("getAssignedScopeOwnershipHistoryRaw", long.class).getReturnType());
        assertEquals(
            TimedEffectOverlay.class,
            RegionDataApi.class.getMethod(
                "createTimedEffectOverlayRaw",
                String.class, long.class, List.class, long.class, long.class, int.class, String.class
            ).getReturnType()
        );
        assertEquals(boolean.class, RegionDataApi.class.getMethod("clearTimedEffectOverlayRaw", long.class, String.class).getReturnType());
        assertEquals(Map.class, RegionDataApi.class.getMethod("queryOverlayRaw", long.class).getReturnType());
        assertEquals(List.class, RegionDataApi.class.getMethod("queryActiveOverlaysRaw", long.class).getReturnType());
    }

    @Test
    public void typedKotlinMethodsRetainTheirDescriptorsAndAreHiddenFromJavaSource() throws Exception {
        assertSynthetic("parseAssignedScopeId-5fYzIHE", String.class);
        assertSynthetic("getScopeByAssignedId-X-bdEC8", long.class);
        assertSynthetic("getAssignedScopeIdOrNull-5fYzIHE", GeoScope.class);
        assertSynthetic("getAssignedScopeOwnershipHistory-X-bdEC8", long.class);
        assertSynthetic(
            "createTimedEffectOverlay-_bZ1-Ao",
            String.class, long.class, List.class, long.class, long.class, int.class, String.class
        );
        assertSynthetic("clearTimedEffectOverlay-KY7rQjY", long.class, String.class);
        assertSynthetic("queryOverlay-X-bdEC8", long.class);
        assertSynthetic("queryActiveOverlays-X-bdEC8", long.class);
    }

    @SuppressWarnings("unused")
    private static void compileRepresentativeCalls(GeoScope scope, String idText, long raw, List<TimedEffect> effects) {
        Long parsed = RegionDataApi.INSTANCE.parseAssignedScopeIdRaw(idText);
        String formatted = RegionDataApi.INSTANCE.formatAssignedScopeIdRaw(raw);
        Pair<Region, GeoScope> resolved = RegionDataApi.INSTANCE.getScopeByAssignedIdRaw(raw);
        Long assignedRaw = RegionDataApi.INSTANCE.getAssignedScopeIdRawOrNull(scope);
        List<ScopeOwnershipEntry> history = RegionDataApi.INSTANCE.getAssignedScopeOwnershipHistoryRaw(raw);
        TimedEffectOverlay overlay = RegionDataApi.INSTANCE.createTimedEffectOverlayRaw(
            "java", raw, effects, 0L, 1L, 0, "test"
        );
        boolean cleared = RegionDataApi.INSTANCE.clearTimedEffectOverlayRaw(raw, "java");
        Map<EffectKey, Integer> effective = RegionDataApi.INSTANCE.queryOverlayRaw(raw);
        List<TimedEffectOverlay> active = RegionDataApi.INSTANCE.queryActiveOverlaysRaw(raw);
    }

    private static void assertSynthetic(String name, Class<?>... parameterTypes) throws Exception {
        Method method = RegionDataApi.class.getDeclaredMethod(name, parameterTypes);
        assertTrue(name + " should be synthetic", method.isSynthetic());
    }
}
