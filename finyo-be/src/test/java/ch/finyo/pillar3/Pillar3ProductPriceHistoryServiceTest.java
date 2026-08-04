package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.MarketDataService;
import ch.finyo.marketdata.PricePoint;
import ch.finyo.marketdata.spi.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for {@link Pillar3ProductPriceHistoryService}.
 *
 * The executor is a same-thread {@code Runnable::run}, so the "asynchronous" backfill runs
 * inline and its interactions are assertable without any waiting.
 */
@ExtendWith(MockitoExtension.class)
class Pillar3ProductPriceHistoryServiceTest {

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String ISIN = "CH0000000016";

    @Mock
    private Pillar3ProductRepository productRepository;

    @Mock
    private MarketDataService marketData;

    private Pillar3ProductPriceHistoryService service;

    @BeforeEach
    void createService() {
        service = new Pillar3ProductPriceHistoryService(productRepository, marketData, Runnable::run);
    }

    private static Pillar3Product product(String isin) {
        return Pillar3Product.builder()
                .id(PRODUCT_ID)
                .provider("UBS")
                .name("Vitainvest 100")
                .isin(isin)
                .equityPct(new BigDecimal("100"))
                .terPct(new BigDecimal("0.50"))
                .active(true)
                .sortOrder(0)
                .build();
    }

    private static PricePoint close(String price, LocalDate date) {
        return new PricePoint(new BigDecimal(price), new CurrencyCode("CHF"), date, DataSource.SIX, false);
    }

    @Test
    void returns_the_stored_closes_with_their_currency_and_never_backfills() {
        LocalDate today = LocalDate.now();
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(ISIN)));
        given(marketData.priceHistory(any(), any())).willReturn(List.of(
                close("140.00", today.minusDays(2)),
                close("142.50", today.minusDays(1))));

        Pillar3PriceHistoryResponse response = service.getPriceHistory(PRODUCT_ID);

        assertThat(response.isin()).isEqualTo(ISIN);
        assertThat(response.currency()).isEqualTo("CHF");
        assertThat(response.points()).containsExactly(
                new Pillar3PriceHistoryResponse.PriceHistoryPoint(today.minusDays(2), new BigDecimal("140.00")),
                new Pillar3PriceHistoryResponse.PriceHistoryPoint(today.minusDays(1), new BigDecimal("142.50")));
        then(marketData).should(never()).backfill(anyString());
    }

    @Test
    void asks_for_a_three_year_window() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(ISIN)));
        given(marketData.priceHistory(any(), any())).willReturn(List.of(close("140.00", LocalDate.now())));

        service.getPriceHistory(PRODUCT_ID);

        LocalDate expectedFrom = LocalDate.now().minusYears(3);
        // ±1 day of slack: the service uses the Swiss zone, the test the system default.
        then(marketData).should().priceHistory(eq(ISIN), argThat((LocalDate from) ->
                from != null
                        && !from.isBefore(expectedFrom.minusDays(1))
                        && !from.isAfter(expectedFrom.plusDays(1))));
    }

    @Test
    void empty_stored_history_returns_no_points_and_triggers_the_backfill() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(ISIN)));
        given(marketData.priceHistory(any(), any())).willReturn(List.of());

        Pillar3PriceHistoryResponse response = service.getPriceHistory(PRODUCT_ID);

        assertThat(response.isin()).isEqualTo(ISIN);
        assertThat(response.currency()).isNull();
        assertThat(response.points()).isEmpty();
        then(marketData).should().backfill(ISIN);
    }

    @Test
    void concurrent_views_of_the_same_fund_collapse_into_one_backfill() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(ISIN)));
        given(marketData.priceHistory(any(), any())).willReturn(List.of());

        // Deferred executor keeps the first backfill "in flight" while the second view arrives.
        List<Runnable> queued = new java.util.ArrayList<>();
        service = new Pillar3ProductPriceHistoryService(productRepository, marketData, queued::add);

        service.getPriceHistory(PRODUCT_ID);
        service.getPriceHistory(PRODUCT_ID);
        queued.forEach(Runnable::run);

        assertThat(queued).hasSize(1);
        then(marketData).should().backfill(ISIN);

        // Once the attempt completed, a later empty view may retry — the fund could be listed now.
        service.getPriceHistory(PRODUCT_ID);
        assertThat(queued).hasSize(2);
    }

    @Test
    void a_failing_backfill_is_swallowed_and_the_empty_history_is_still_returned() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(ISIN)));
        given(marketData.priceHistory(any(), any())).willReturn(List.of());
        given(marketData.backfill(ISIN)).willThrow(new IllegalStateException("provider down"));

        Pillar3PriceHistoryResponse response = service.getPriceHistory(PRODUCT_ID);

        assertThat(response.points()).isEmpty();
    }

    @Test
    void a_product_without_an_isin_yields_an_empty_history_and_no_marketdata_call() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(null)));

        Pillar3PriceHistoryResponse response = service.getPriceHistory(PRODUCT_ID);

        assertThat(response.isin()).isNull();
        assertThat(response.currency()).isNull();
        assertThat(response.points()).isEmpty();
        then(marketData).shouldHaveNoInteractions();
    }

    @Test
    void an_unknown_product_is_a_404() {
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPriceHistory(PRODUCT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(PRODUCT_ID.toString());
        then(marketData).shouldHaveNoInteractions();
    }
}
