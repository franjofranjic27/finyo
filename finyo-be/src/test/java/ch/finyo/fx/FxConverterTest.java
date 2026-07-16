package ch.finyo.fx;

import ch.finyo.common.money.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for FxConverter.
 *
 * Three behaviours carry the type's promise: a foreign amount is converted at the stored rate; a
 * CHF or unknown-currency amount passes through untouched and without a database read; and a
 * currency with no rate becomes {@link FxConversion.Unconvertible} rather than a guess. The last is
 * the one that keeps a missing rate from becoming a wrong total.
 */
@DisplayName("FxConverter")
@ExtendWith(MockitoExtension.class)
class FxConverterTest {

    private static final LocalDate ON = LocalDate.of(2026, 7, 14);
    private static final CurrencyCode USD = new CurrencyCode("USD");

    @Mock
    private FxRateRepository repository;

    @InjectMocks
    private FxConverter converter;

    private void givenRate(String currency, String chfPerUnit, LocalDate rateDate) {
        FxRate rate = FxRate.builder()
                .currency(currency).rateDate(rateDate).chfPerUnit(new BigDecimal(chfPerUnit))
                .rateType(FxRateType.MID).source("frankfurter")
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        given(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                eq(currency), eq(FxRateType.MID), any())).willReturn(Optional.of(rate));
    }

    @Test
    void converts_a_foreign_amount_at_the_stored_rate_and_carries_the_provenance() {
        givenRate("USD", "0.80", LocalDate.of(2026, 7, 13));

        FxConversion result = converter.toChf(new BigDecimal("1000"), USD, ON, FxRateType.MID);

        assertThat(result).isInstanceOfSatisfying(FxConversion.Converted.class, converted -> {
            assertThat(converted.chf()).isEqualByComparingTo("800.00");
            assertThat(converted.rate()).isEqualByComparingTo("0.80");
            assertThat(converted.rateDate()).isEqualTo(LocalDate.of(2026, 7, 13));
            assertThat(converted.type()).isEqualTo(FxRateType.MID);
            assertThat(converted.source()).isEqualTo("frankfurter");
        });
    }

    @Test
    void passes_a_chf_amount_through_by_identity_without_a_lookup() {
        // A CHF amount is already in CHF; consulting the rate table for it would be a pointless
        // read on the hot path, and attaching a rate would be a fiction.
        FxConversion result = converter.toChf(new BigDecimal("500"), CurrencyCode.CHF, ON, FxRateType.MID);

        assertThat(result).isInstanceOfSatisfying(FxConversion.Converted.class, converted -> {
            assertThat(converted.chf()).isEqualByComparingTo("500");
            assertThat(converted.rate()).isNull();
            assertThat(converted.rateDate()).isNull();
        });
        then(repository).should(never())
                .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(any(), any(), any());
    }

    @Test
    void passes_an_unknown_currency_through_by_identity_rather_than_dropping_it() {
        // A null currency (OpenFIGI publishes none) is taken at face value. Excluding it would
        // drop a real holding from the total; assigning it a rate would invent one.
        FxConversion result = converter.toChf(new BigDecimal("500"), null, ON, FxRateType.MID);

        assertThat(result).isInstanceOf(FxConversion.Converted.class);
        assertThat(((FxConversion.Converted) result).chf()).isEqualByComparingTo("500");
    }

    @Test
    void reports_unconvertible_when_no_rate_exists_for_the_currency() {
        // The repository has nothing at or before the date. Never interpolated, never guessed —
        // the caller must exclude it, not fold a made-up rate into the total.
        given(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                eq("USD"), eq(FxRateType.MID), any())).willReturn(Optional.empty());

        FxConversion result = converter.toChf(new BigDecimal("1000"), USD, ON, FxRateType.MID);

        assertThat(result).isInstanceOfSatisfying(FxConversion.Unconvertible.class,
                unconvertible -> assertThat(unconvertible.currency()).isEqualTo(USD));
    }
}
