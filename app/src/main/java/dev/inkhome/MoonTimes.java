package dev.inkhome;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static java.lang.Math.*;

/** Approximate sea-level upper-limb crossings for a civil date, using the leading
 * periodic terms of Meeus, Astronomical Algorithms, chapter 47. East longitude is positive. */
final class MoonTimes {
    Instant rise;
    Instant set;

    static MoonTimes on(LocalDate day, ZoneId zone, double latitude, double longitude) {
        MoonTimes events = new MoonTimes();
        long start = day.atStartOfDay(zone).toEpochSecond();
        long end = day.plusDays(1).atStartOfDay(zone).toEpochSecond();
        boolean above = height(start, latitude, longitude) >= 0;
        for (long a = start; a < end; a += 300) {
            long b = min(a + 300, end);
            boolean next = height(b, latitude, longitude) >= 0;
            if (above != next) {
                long lo = a, hi = b;
                while (hi - lo > 1) {
                    long mid = (hi + lo) / 2;
                    if ((height(mid, latitude, longitude) >= 0) == above) lo = mid;
                    else hi = mid;
                }
                if (hi < end) {
                    if (next && events.rise == null) events.rise = Instant.ofEpochSecond(hi);
                    if (!next && events.set == null) events.set = Instant.ofEpochSecond(hi);
                }
            }
            above = next;
        }
        return events;
    }

    private static double height(long seconds, double lat, double lon) {
        double days = seconds / 86400.0 - 10957.5; // Julian days since J2000.0
        double l = toRadians(218.316448 + 13.17639648 * days);
        double m = toRadians(134.963396 + 13.06499295 * days);
        double s = toRadians(357.529109 + 0.98560028 * days);
        double d = toRadians(297.850192 + 12.19074912 * days);
        double f = toRadians(93.272095 + 13.22935024 * days);
        double lambda = l + toRadians(6.289 * sin(m) + 1.274 * sin(2*d-m)
                + 0.658 * sin(2*d) + 0.214 * sin(2*m) - 0.185 * sin(s)
                - 0.114 * sin(2*f) + 0.059 * sin(2*d-2*m)
                + 0.057 * sin(2*d-s-m) + 0.053 * sin(2*d+m) + 0.046 * sin(2*d-s));
        double beta = toRadians(5.128 * sin(f) + 0.280 * sin(m+f) + 0.277 * sin(m-f)
                + 0.173 * sin(2*d-f) + 0.055 * sin(2*d-m+f)
                + 0.046 * sin(2*d-m-f) + 0.033 * sin(2*d+f) + 0.017 * sin(2*m+f));
        double epsilon = toRadians(23.439291 - 0.000000356 * days);
        double ra = atan2(sin(lambda)*cos(epsilon) - tan(beta)*sin(epsilon), cos(lambda));
        double dec = asin(sin(beta)*cos(epsilon) + cos(beta)*sin(epsilon)*sin(lambda));
        double angle = toRadians(280.460618 + 360.98564737 * days + lon) - ra;
        double phi = toRadians(lat);
        double altitude = asin(sin(phi)*sin(dec) + cos(phi)*cos(dec)*cos(angle));
        double distanceKm = 385001 - 20905*cos(m) - 3699*cos(2*d-m) - 2956*cos(2*d) - 570*cos(2*m);
        // USNO upper-limb definition: parallax less semidiameter and 34 arcmin refraction.
        return altitude - asin(6378.14 / distanceKm) + asin(1737.4 / distanceKm) + toRadians(34.0/60);
    }
}
