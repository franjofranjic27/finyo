package ch.finyo.integration.six;

import ch.finyo.marketdata.spi.PriceBar;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FqsChartResponse.toBars() — the pure mapping from charts.json's parallel
 * Date/Close arrays to a list of bars.
 *
 * The wire deserialisation itself is SixHistoryAdapterTest's subject (it runs the real Jackson
 * against a loopback server). This class works one level down, on the shape after parsing: that
 * {@code Date[i]} and {@code Close[i]} belong together, and that anything meaningless — a
 * missing array, a null cell, a non-positive close — is dropped rather than carried into a chart
 * as a real number.
 */
@DisplayName("FqsChartResponse.toBars")
class FqsChartResponseTest {

    private static FqsChartResponse withData(List<Long> dates, List<BigDecimal> closes) {
        return new FqsChartResponse(List.of(new FqsChartResponse.Valor(
                new FqsChartResponse.Data(dates, closes))));
    }

    // =========================================================================
    // The happy path
    // =========================================================================

    @Test
    void pairs_each_date_with_the_close_at_the_same_index_oldest_first() {
        FqsChartResponse response = withData(
                Arrays.asList(20260105L, 20260106L, 20260107L),
                Arrays.asList(new BigDecimal("144.20"), new BigDecimal("143.94"), new BigDecimal("145.10")));

        List<PriceBar> bars = response.toBars();

        assertThat(bars).hasSize(3);
        assertThat(bars).extracting(PriceBar::date).containsExactly(
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7));
        assertThat(bars).extracting(PriceBar::close).containsExactly(
                new BigDecimal("144.20"), new BigDecimal("143.94"), new BigDecimal("145.10"));
    }

    @Test
    void reads_the_yyyyMMdd_integer_into_a_local_date() {
        List<PriceBar> bars = withData(List.of(20260105L), List.of(new BigDecimal("144.20"))).toBars();

        assertThat(bars.getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 5));
    }

    // =========================================================================
    // Nothing to map
    // =========================================================================

    @Nested
    @DisplayName("an absent or empty payload yields an empty list, never a failure")
    class NothingToMap {

        @Test
        void null_valors_list() {
            assertThat(new FqsChartResponse(null).toBars()).isEmpty();
        }

        @Test
        void empty_valors_list() {
            assertThat(new FqsChartResponse(List.of()).toBars()).isEmpty();
        }

        @Test
        void null_data_object() {
            FqsChartResponse response = new FqsChartResponse(List.of(new FqsChartResponse.Valor(null)));

            assertThat(response.toBars()).isEmpty();
        }

        @Test
        void null_date_array() {
            assertThat(withData(null, List.of(new BigDecimal("144.20"))).toBars()).isEmpty();
        }

        @Test
        void null_close_array() {
            assertThat(withData(List.of(20260105L), null).toBars()).isEmpty();
        }

        @Test
        void both_arrays_empty() {
            assertThat(withData(List.of(), List.of()).toBars()).isEmpty();
        }
    }

    // =========================================================================
    // Dropping the meaningless cells
    // =========================================================================

    @Nested
    @DisplayName("drops any cell that would carry a non-price into a chart")
    class DropsMeaninglessCells {

        @Test
        void drops_a_bar_whose_close_is_zero() {
            // SIX emits 0 for a day the instrument was listed but did not trade. A zero would
            // flow into a valuation as a real close of nothing.
            List<PriceBar> bars = withData(
                    Arrays.asList(20260105L, 20260106L),
                    Arrays.asList(new BigDecimal("144.20"), BigDecimal.ZERO)).toBars();

            assertThat(bars).extracting(PriceBar::date).containsExactly(LocalDate.of(2026, 1, 5));
        }

        @Test
        void drops_a_bar_whose_close_is_negative() {
            List<PriceBar> bars = withData(
                    Arrays.asList(20260105L, 20260106L),
                    Arrays.asList(new BigDecimal("-5"), new BigDecimal("145.10"))).toBars();

            assertThat(bars).extracting(PriceBar::date).containsExactly(LocalDate.of(2026, 1, 6));
        }

        @Test
        void drops_a_bar_whose_close_is_null() {
            List<PriceBar> bars = withData(
                    Arrays.asList(20260105L, 20260106L),
                    Arrays.asList(null, new BigDecimal("145.10"))).toBars();

            assertThat(bars).extracting(PriceBar::date).containsExactly(LocalDate.of(2026, 1, 6));
        }

        @Test
        void drops_a_bar_whose_date_is_null() {
            List<PriceBar> bars = withData(
                    Arrays.asList(null, 20260106L),
                    Arrays.asList(new BigDecimal("144.20"), new BigDecimal("145.10"))).toBars();

            assertThat(bars).extracting(PriceBar::date).containsExactly(LocalDate.of(2026, 1, 6));
        }
    }

    // =========================================================================
    // Ragged arrays
    // =========================================================================

    @Test
    void zips_only_up_to_the_shorter_of_the_two_arrays() {
        // A truncated feed must not index past the end of the shorter array — a defensive
        // guard against a response that lost cells in transit.
        List<PriceBar> bars = withData(
                Arrays.asList(20260105L, 20260106L, 20260107L),
                Arrays.asList(new BigDecimal("144.20"), new BigDecimal("143.94"))).toBars();

        assertThat(bars).extracting(PriceBar::date)
                .containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6));
    }
}
