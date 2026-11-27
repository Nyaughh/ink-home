package dev.inkhome;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.Assert.*;

public class MoonTimesTest {
    // US Naval Observatory, 2023 Moonrise/Moonset table, 30 N, 90 W, 6h west of Greenwich.
    // https://aa.usno.navy.mil/calculated/rstt/year?lat=30&lon=-90&task=1&tz=6&tz_sign=-1&year=2023
    private static final ZoneOffset CENTRAL = ZoneOffset.ofHours(-6);

    private void near(String expected, Instant actual) {
        assertNotNull(actual);
        assertTrue(expected + " vs " + actual,
                Math.abs(Instant.parse(expected).getEpochSecond() - actual.getEpochSecond()) < 5 * 60);
    }

    @Test public void setBeforeRise() {
        MoonTimes t = MoonTimes.on(LocalDate.of(2023, 1, 1), CENTRAL, 30, -90);
        near("2023-01-01T19:23:00Z", t.rise);
        near("2023-01-01T08:12:00Z", t.set);
    }

    @Test public void noRiseAndNoSetDays() {
        MoonTimes noRise = MoonTimes.on(LocalDate.of(2023, 1, 14), CENTRAL, 30, -90);
        assertNull(noRise.rise);
        near("2023-01-14T17:26:00Z", noRise.set);
        MoonTimes noSet = MoonTimes.on(LocalDate.of(2023, 1, 27), CENTRAL, 30, -90);
        near("2023-01-27T16:52:00Z", noSet.rise);
        assertNull(noSet.set);
    }

    @Test public void springForwardUsesLocalMidnights() {
        MoonTimes t = MoonTimes.on(LocalDate.of(2023, 3, 12), ZoneId.of("America/Chicago"), 30, -90);
        // The 23:17 standard-time rise in the USNO table is next day's 00:17 in Chicago.
        assertNull(t.rise);
        near("2023-03-12T15:05:00Z", t.set);
    }

    @Test public void bothEventsAbsentAtNorthPole() {
        MoonTimes t = MoonTimes.on(LocalDate.of(2023, 1, 7), ZoneOffset.UTC, 90, 0);
        assertNull(t.rise);
        assertNull(t.set);
    }
}
