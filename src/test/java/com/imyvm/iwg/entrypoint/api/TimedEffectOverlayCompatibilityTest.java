package com.imyvm.iwg.entrypoint.api;

import com.imyvm.iwg.domain.TimedEffect;
import com.imyvm.iwg.domain.TimedEffectOverlay;
import com.imyvm.iwg.domain.component.EffectKey;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class TimedEffectOverlayCompatibilityTest {
    private static final long ASSIGNED_SCOPE_ID_RAW = Long.MIN_VALUE + 1L;

    @Test
    public void exposesEpochAccessorsWhileRetainingLegacyJavaCalls() {
        List<TimedEffect> effects = List.of(new TimedEffect(EffectKey.SPEED, 1));
        TimedEffectOverlay overlay = new TimedEffectOverlay(
            "overlay", ASSIGNED_SCOPE_ID_RAW, effects, 100L, 200L, 0, "test"
        );

        assertEquals(100L, overlay.getStartTickMillis());
        assertEquals(200L, overlay.getEndTickMillis());
        assertEquals(100L, overlay.getStartEpochMillis());
        assertEquals(200L, overlay.getEndEpochMillis());
        assertEquals(100L, overlay.component4());
        assertEquals(200L, overlay.component5());

        TimedEffectOverlay copied = overlay.copy(
            "overlay", ASSIGNED_SCOPE_ID_RAW, effects, 125L, 225L, 0, "test"
        );
        assertEquals(125L, copied.getStartEpochMillis());
        assertEquals(225L, copied.getEndEpochMillis());
    }

    @Test
    public void retainsConstructorGetterComponentAndCopyDescriptors() throws Exception {
        assertEquals(
            TimedEffectOverlay.class,
            TimedEffectOverlay.class.getConstructor(
                String.class, long.class, List.class, long.class, long.class, int.class, String.class
            ).getDeclaringClass()
        );
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("getStartTickMillis").getReturnType());
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("getEndTickMillis").getReturnType());
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("getStartEpochMillis").getReturnType());
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("getEndEpochMillis").getReturnType());
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("component4").getReturnType());
        assertEquals(long.class, TimedEffectOverlay.class.getMethod("component5").getReturnType());
        assertEquals(
            TimedEffectOverlay.class,
            TimedEffectOverlay.class.getMethod(
                "copy", String.class, long.class, List.class, long.class, long.class, int.class, String.class
            ).getReturnType()
        );
        assertEquals(
            TimedEffectOverlay.class,
            TimedEffectOverlay.class.getDeclaredMethod(
                "copy$default",
                TimedEffectOverlay.class,
                String.class,
                long.class,
                List.class,
                long.class,
                long.class,
                int.class,
                String.class,
                int.class,
                Object.class
            ).getReturnType()
        );
    }
}
