package volumet.project.playground.records;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * W1 records, part 3: cross-field rules, static factories and derived values. YOUR IMPLEMENTATION GOES HERE.
 * <p>
 * A record can't declare extra instance fields: everything it holds is in the header. Anything else is computed
 * by a method ({@link #days()}), and alternative ways to create one are static factories ({@link #of},
 * {@link #singleDay}).
 * <p>
 * Rules (spec: {@code DateRangeTest}):
 * <ul>
 *   <li>{@code from} and {@code to} must not be null ({@link NullPointerException});</li>
 *   <li>{@code from} must not be after {@code to} ({@link IllegalArgumentException}); equal is allowed (one day);</li>
 *   <li>both ends are inclusive.</li>
 * </ul>
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException();
        }
    }

    public static DateRange of(LocalDate from, LocalDate to) {
        return new DateRange(from, to);
    }

    public static DateRange singleDay(LocalDate day) {
        return new DateRange(day, day);
    }

    /** Number of days, both ends included: 2026-01-01..2026-01-01 is 1 day. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate day) {
        Objects.requireNonNull(day);
        return (!day.isAfter(to) && !day.isBefore(from));
    }

    /** {@code true} if the two ranges share at least one day. */
    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other);
        return !from.isAfter(other.to) && !other.from.isAfter(to);
    }
}
