package ch.finyo.fx;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One exchange rate: how many CHF one unit of a foreign currency was worth on one day, from one
 * kind of quote.
 *
 * Tenant-free, like {@link ch.finyo.marketdata.InstrumentPrice} and for the same reason — the
 * EUR/CHF rate on 14 July is the same fact for everyone. Keyed on (currency, date, type) so a mid
 * rate and an official rate for the same currency and day coexist rather than overwrite.
 *
 * <p>Only one direction is representable: {@code chfPerUnit}, CHF per one unit of {@link #currency}.
 * That is deliberate. The two vendors disagree on direction — Frankfurter returns units-per-CHF,
 * BAZG returns CHF-per-unit — and each adapter normalises to this single form before constructing
 * a {@code FxRate}, so a rate in the wrong direction never reaches the domain. See ADR-009.
 *
 * <p>This type doubles as the adapters' return value (a {@code SourceResult<FxRate>}); the same
 * object the provider builds is what the writer persists. It is a plain POJO until then — the
 * native upsert writes it, so it never rides the JPA lifecycle as a managed provider result.
 */
@Entity
@Table(name = "fx_rate")
@IdClass(FxRate.Key.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FxRate {

    /** The foreign currency, ISO 4217. Stored as a plain three-letter code, like {@code isin}. */
    @Id
    @Column(length = 3)
    private String currency;

    @Id
    @Column(name = "rate_date")
    private LocalDate rateDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", length = 20)
    private FxRateType rateType;

    /** CHF per one unit of {@link #currency}. Eight decimals — a rate is not a money amount. */
    @Column(name = "chf_per_unit", nullable = false, precision = 19, scale = 8)
    private BigDecimal chfPerUnit;

    /** The provider that produced it: {@code frankfurter} or {@code bazg}. */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "retrieved_at", nullable = false)
    private OffsetDateTime retrievedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private String currency;
        private LocalDate rateDate;
        private FxRateType rateType;
    }
}
