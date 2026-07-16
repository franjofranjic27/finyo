package ch.finyo.marketdata;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * The closing price of one security on one trading day.
 *
 * Tenant-free, like {@link CachedSecurityReference} and for the same reason: the close of
 * IE00B4L5Y983 on 14 July is the same fact for everybody. It is keyed on (ISIN, date), so
 * it is a time series rather than a single "last price" — which makes it the thing that
 * {@code portfolio_snapshot} is not, and the two are complementary rather than redundant:
 * this is a market fact, a snapshot is a user fact (it depends on what they held that day).
 *
 * This table is what takes SIX off the read path. The portfolio is priced from here, never
 * from an HTTP call — so a hanging vendor cannot stall a page load, and an unreachable one
 * degrades the answer instead of failing it.
 */
@Entity
@Table(name = "instrument_price")
@IdClass(InstrumentPrice.Key.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentPrice {

    @Id
    @Column(length = 12)
    private String isin;

    @Id
    @Column(name = "price_date")
    private LocalDate priceDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal close;

    /** Never defaulted. A price without its currency is a number without a meaning. */
    @Column(length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    @Column(name = "retrieved_at", nullable = false)
    private OffsetDateTime retrievedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String isin;
        private LocalDate priceDate;
    }
}
