package ch.finyo.analytics;

import ch.finyo.common.SwissTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the TimeRange enum.
 *
 * TimeRange derives its boundaries from "today" in the Swiss business time
 * zone. The tests express every expectation relative to the same anchor
 * (LocalDate.now(SwissTime.ZONE)), so they hold on any day of the month and
 * do not depend on a mocked clock.
 */
class TimeRangeTest {

    private final LocalDate today = LocalDate.now(SwissTime.ZONE);

    // =========================================================================
    // startDate()
    // =========================================================================

    @Test
    void last_7_days_starts_seven_days_ago() {
        assertThat(TimeRange.LAST_7_DAYS.startDate()).isEqualTo(today.minusDays(7));
    }

    @Test
    void last_30_days_starts_thirty_days_ago() {
        assertThat(TimeRange.LAST_30_DAYS.startDate()).isEqualTo(today.minusDays(30));
    }

    @Test
    void this_month_starts_on_the_first_of_the_current_month() {
        assertThat(TimeRange.THIS_MONTH.startDate()).isEqualTo(today.withDayOfMonth(1));
    }

    @Test
    void last_month_starts_on_the_first_of_the_previous_month() {
        assertThat(TimeRange.LAST_MONTH.startDate()).isEqualTo(today.minusMonths(1).withDayOfMonth(1));
    }

    @Test
    void last_3_months_starts_on_a_month_boundary_three_months_back() {
        assertThat(TimeRange.LAST_3_MONTHS.startDate()).isEqualTo(today.minusMonths(3).withDayOfMonth(1));
    }

    @Test
    void last_6_months_starts_on_a_month_boundary_six_months_back() {
        assertThat(TimeRange.LAST_6_MONTHS.startDate()).isEqualTo(today.minusMonths(6).withDayOfMonth(1));
    }

    @Test
    void last_12_months_starts_on_a_month_boundary_twelve_months_back() {
        assertThat(TimeRange.LAST_12_MONTHS.startDate()).isEqualTo(today.minusMonths(12).withDayOfMonth(1));
    }

    // =========================================================================
    // endDate()
    // =========================================================================

    @Test
    void last_month_ends_on_the_last_day_of_the_previous_month() {
        LocalDate lastMonth = today.minusMonths(1);

        assertThat(TimeRange.LAST_MONTH.endDate())
                .isEqualTo(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
    }

    @Test
    void all_other_ranges_end_today() {
        assertThat(TimeRange.LAST_7_DAYS.endDate()).isEqualTo(today);
        assertThat(TimeRange.LAST_30_DAYS.endDate()).isEqualTo(today);
        assertThat(TimeRange.THIS_MONTH.endDate()).isEqualTo(today);
        assertThat(TimeRange.LAST_3_MONTHS.endDate()).isEqualTo(today);
        assertThat(TimeRange.LAST_6_MONTHS.endDate()).isEqualTo(today);
        assertThat(TimeRange.LAST_12_MONTHS.endDate()).isEqualTo(today);
    }

    @ParameterizedTest
    @EnumSource(TimeRange.class)
    void every_range_starts_before_or_on_its_end_date(TimeRange range) {
        assertThat(range.startDate())
                .as("startDate of %s must not lie after endDate", range)
                .isBeforeOrEqualTo(range.endDate());
    }
}
