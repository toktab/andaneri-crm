package ge.andaneri.crm.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * When a customer is likely to need another order. Their own interval wins if someone typed one
 * in; otherwise the average gap between their past orders; otherwise the team default.
 */
public final class Reorder {

    private Reorder() {
    }

    /** Average days between consecutive orders, or null with fewer than two orders on different days. */
    public static Integer averageGap(List<LocalDate> sortedDates) {
        if (sortedDates == null || sortedDates.size() < 2) {
            return null;
        }
        long span = ChronoUnit.DAYS.between(sortedDates.get(0), sortedDates.get(sortedDates.size() - 1));
        if (span <= 0) {
            return null;
        }
        return (int) Math.max(1, Math.round((double) span / (sortedDates.size() - 1)));
    }

    public static int expectedDays(Integer ownInterval, Integer averageGap, int teamDefault) {
        if (ownInterval != null && ownInterval > 0) {
            return ownInterval;
        }
        return averageGap != null ? averageGap : teamDefault;
    }
}
