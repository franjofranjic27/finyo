package ch.finyo.marketdata;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Persisted master data for one security.
 *
 * Deliberately has no {@code user_id}: the fact that IE00B4L5Y983 is an iShares
 * ETF quoted in USD is the same for every user. Row-level tenancy (ADR-001) applies
 * to user data, not to market facts — a per-user copy would be duplication with a
 * consistency risk attached.
 *
 * This is the reason the module exists separately from {@code investment}, whose
 * {@code Instrument} <em>is</em> tenant-scoped.
 */
@Entity
@Table(name = "security_reference")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CachedSecurityReference {

    /** The ISIN is the natural key — every provider we use resolves to one. */
    @Id
    @Column(length = 12)
    private String isin;

    @Column(length = 20)
    private String valor;

    @Column(length = 20)
    private String ticker;

    @Column(length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityType type;

    @Column(length = 3)
    private CurrencyCode currency;

    @Column(length = 255)
    private String issuer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataSource source;

    @Column(name = "retrieved_at", nullable = false)
    private OffsetDateTime retrievedAt;

    public SecurityReference toReference() {
        return new SecurityReference(isin, valor, ticker, name, type, currency, issuer, source, retrievedAt);
    }

    // Safe to base on the key because the key is assigned, not generated: an ISIN is
    // known before the row exists and never changes afterwards, so identity is stable
    // across the detached/persistent boundary. (For a @GeneratedValue id this would be
    // the classic JPA trap — null before the flush, different after.)
    @Override
    public boolean equals(Object other) {
        return other instanceof CachedSecurityReference that && isin != null && isin.equals(that.isin);
    }

    @Override
    public int hashCode() {
        return isin == null ? 0 : isin.hashCode();
    }
}
