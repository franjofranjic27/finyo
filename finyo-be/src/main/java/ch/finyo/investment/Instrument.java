package ch.finyo.investment;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "instrument")
@EntityListeners(AuditingEntityListener.class)
@Getter
// toBuilder: entities are immutable — every update copies via toBuilder() so
// new columns can never be wiped by a forgotten field in a manual copy.
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 20)
    private String valor;

    @Column(length = 12)
    private String isin;

    @Column(length = 20)
    private String ticker;

    @Column(length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type")
    private InstrumentType instrumentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    @Builder.Default
    private AssetClass assetClass = AssetClass.STOCK;

    /**
     * Trading currency. Until V34 this did not exist, which meant a USD ETF was summed
     * into the portfolio total as if it were CHF — the total was simply wrong. FX
     * conversion arrives in PR 4; this column is what makes it possible.
     *
     * <b>Nullable, and it has to be.</b> Null means "we do not know" — OpenFIGI publishes
     * no currency at all, so an instrument resolved through it genuinely has none. A
     * {@code NOT NULL DEFAULT 'CHF'} would make an unknown currency indistinguishable
     * from a verified Swiss one and hand PR 4's converter a guess dressed as a fact,
     * which is the original bug wearing a new hat.
     */
    @Column(length = 3)
    private CurrencyCode currency;

    /**
     * Where the master data came from — and how much it is worth. MANUAL is the user,
     * SIX/OPENFIGI are verified, HEURISTIC means nobody knew the security and its asset
     * class was guessed from the name, UNRESOLVED means the providers could not be
     * reached and it still needs another attempt.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DataSource source = DataSource.MANUAL;

    /** Total expense ratio in percent, e.g. 0.20 for 0.20 %. */
    @Column(precision = 5, scale = 2)
    private BigDecimal ter;

    // The uploaded factsheet PDF lives in instrument_factsheet (own entity):
    // keeping the blob off this hot entity avoids N x 10 MB heap amplification
    // on every portfolio/detail read.
    @Column(name = "factsheet_url", length = 500)
    private String factsheetUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "last_price", precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "last_price_updated_at")
    private OffsetDateTime lastPriceUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Preferred market-data identifier: valor, then ISIN, then ticker; null when none is set. */
    public String preferredIdentifier() {
        if (valor != null) {
            return valor;
        }
        if (isin != null) {
            return isin;
        }
        return ticker;
    }
}
