package volumet.project.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DateUtils {
    private static volatile Clock clock = Clock.systemDefaultZone();

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static LocalDateTime todayWithTime() {
        return LocalDateTime.now(clock);
    }

    static void setClock(Clock modifiedClock) {
        clock = modifiedClock;
    }

    static void reset() {
        clock = Clock.systemDefaultZone();
    }

    public static boolean isBeforeOrEquals(LocalDate date1, LocalDate date2) {
        if (date1 == null && date2 == null) {
            return true;
        }

        if (date1 == null) {
            return false;
        }

        if (date2 == null) {
            return true;
        }

        return isBefore(date1, date2) || date1.isEqual(date2);
    }

    public static boolean isAfterOrEquals(LocalDate date1, LocalDate date2) {
        if (date1 == null && date2 == null) {
            return true;
        }

        return isAfter(date1, date2) || date1.isEqual(date2);
    }

    public static boolean isAfter(LocalDate date1, LocalDate date2) {
        if (date1 == null && date2 == null) {
            return false;
        }

        if (date1 == null) {
            return true;
        }

        if (date2 == null) {
            return false;
        }

        return date1.isAfter(date2);
    }

    public static boolean isBefore(LocalDate date1, LocalDate date2) {
        if (date1 == null && date2 == null) {
            return false;
        }

        if (date1 == null) {
            return false;
        }

        if (date2 == null) {
            return true;
        }

        return date1.isBefore(date2);
    }
}
