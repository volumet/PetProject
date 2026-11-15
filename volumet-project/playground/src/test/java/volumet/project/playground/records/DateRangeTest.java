package volumet.project.playground.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_10 = LocalDate.of(2026, 1, 10);

    @Test
    @DisplayName("from after to is rejected; from == to is one day")
    void crossFieldValidation() {
        assertThatThrownBy(() -> new DateRange(JAN_10, JAN_1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new DateRange(JAN_1, JAN_1).days()).isEqualTo(1);
    }

    @Test
    @DisplayName("null ends are rejected")
    void rejectsNulls() {
        assertThatThrownBy(() -> new DateRange(null, JAN_1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateRange(JAN_1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("static factories build the same values as the constructor, and are validated too")
    void factories() {
        assertThat(DateRange.of(JAN_1, JAN_10)).isEqualTo(new DateRange(JAN_1, JAN_10));
        assertThat(DateRange.singleDay(JAN_1)).isEqualTo(new DateRange(JAN_1, JAN_1));
        assertThatThrownBy(() -> DateRange.of(JAN_10, JAN_1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("days() counts both ends")
    void days() {
        assertThat(DateRange.of(JAN_1, JAN_10).days()).isEqualTo(10);
        assertThat(DateRange.of(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 2)).days()).isEqualTo(4);
        // spans of a month or more: the total day count, not just the "days" part of a Period
        assertThat(DateRange.of(JAN_1, LocalDate.of(2026, 2, 1)).days()).isEqualTo(32);
        assertThat(DateRange.of(JAN_1, LocalDate.of(2026, 12, 31)).days()).isEqualTo(365);
    }

    @Test
    @DisplayName("contains() includes both ends")
    void contains() {
        DateRange range = DateRange.of(JAN_1, JAN_10);

        assertThat(range.contains(JAN_1)).isTrue();
        assertThat(range.contains(JAN_10)).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 1, 5))).isTrue();
        assertThat(range.contains(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(range.contains(LocalDate.of(2026, 1, 11))).isFalse();
    }

    @Test
    @DisplayName("overlaps() is true when the ranges share at least one day, including touching ends")
    void overlaps() {
        DateRange range = DateRange.of(JAN_1, JAN_10);

        assertThat(range.overlaps(DateRange.of(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20)))).isTrue();
        assertThat(range.overlaps(DateRange.of(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1)))).isTrue();
        assertThat(range.overlaps(DateRange.of(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4)))).isTrue();
        assertThat(range.overlaps(DateRange.of(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 20)))).isFalse();
        assertThat(range.overlaps(DateRange.of(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)))).isFalse();
    }
}
