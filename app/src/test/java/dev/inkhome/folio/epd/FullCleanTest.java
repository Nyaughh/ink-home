package dev.inkhome.folio.epd;

import android.view.ViewGroup;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/** Checks the waveform at the firmware boundary for activity and overlay requests. */
public class FullCleanTest {
    @Test public void manualPeriodicAndOverlayCleansRequestGc98() {
        ViewGroup target = mock(ViewGroup.class);
        when(target.isAttachedToWindow()).thenReturn(true);
        OnyxEpd driver = new OnyxEpd(false);
        try (MockedStatic<OnyxEpd> firmware = mockStatic(OnyxEpd.class, CALLS_REAL_METHODS)) {
            firmware.when(() -> OnyxEpd.requestRefresh(target, 98)).thenReturn(true);
            driver.clean(target, true);
            driver.clean(target, false);
            assertTrue(Epd.requestFullClean(target));
            firmware.verify(() -> OnyxEpd.requestRefresh(target, 98), times(3));
        }
    }
}
