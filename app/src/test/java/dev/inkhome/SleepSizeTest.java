package dev.inkhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SleepSizeTest {
    @Test public void c69UsesEveryPanelPixelInEitherRotation() {
        SleepSize portrait = SleepSize.rotated(758, 1024, false);
        SleepSize landscape = SleepSize.rotated(758, 1024, true);
        assertEquals(758, portrait.width);
        assertEquals(1024, portrait.height);
        assertEquals(1024, landscape.width);
        assertEquals(758, landscape.height);
        assertNotEquals(portrait.key(), landscape.key());
        assertEquals(360, Math.round(236 * portrait.artworkScale()));
    }

    @Test public void naturalLandscapePanelRotatesToPortrait() {
        SleepSize size = SleepSize.rotated(1920, 1080, true);
        assertEquals(1080, size.width);
        assertEquals(1920, size.height);
        assertEquals(1080 / 497f, size.artworkScale(), 0.0001f);
        assertEquals(1080 / (1024f * 497f / 758f),
                SleepSize.rotated(1920, 1080, false).artworkScale(), 0.0001f);
    }

    @Test public void fillCropsAndFitLeavesMargins() {
        SleepSize panel = new SleepSize(1200, 1600);
        assertEquals(2f, panel.imageScale(1600, 800, true), 0f);
        assertEquals(0.75f, panel.imageScale(1600, 800, false), 0f);
        assertEquals(3f, panel.imageScale(400, 1000, true), 0f);
        assertEquals(1.6f, panel.imageScale(400, 1000, false), 0f);
    }
}
