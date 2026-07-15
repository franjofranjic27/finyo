package ch.finyo.fx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, FxRate.Key> {

    /**
     * The latest rate of a currency and type at or before a date.
     *
     * "At or before", not "on": a Saturday, a holiday or the day before the first sync has no rate
     * of its own, and the honest answer is the most recent real one — never an interpolated value
     * invented to fill the gap.
     */
    Optional<FxRate> findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
            String currency, FxRateType rateType, LocalDate on);

    /** How much history a currency already has — the sync backfills only when this is thin. */
    long countByCurrencyAndRateType(String currency, FxRateType rateType);

    /**
     * The same currency, day and type arriving twice — a nightly sync racing a manual trigger, or
     * a backfill re-run — is an update, not a crash. A rate is a fact about the market: the later
     * read of it wins.
     */
    @Modifying
    @Query(value = """
            INSERT INTO fx_rate (currency, rate_date, chf_per_unit, rate_type, source, retrieved_at)
            VALUES (:currency, :rateDate, :chfPerUnit, :rateType, :source, :retrievedAt)
            ON CONFLICT (currency, rate_date, rate_type) DO UPDATE SET
                chf_per_unit = EXCLUDED.chf_per_unit,
                source       = EXCLUDED.source,
                retrieved_at = EXCLUDED.retrieved_at
            """, nativeQuery = true)
    void upsert(@Param("currency") String currency,
                @Param("rateDate") LocalDate rateDate,
                @Param("chfPerUnit") BigDecimal chfPerUnit,
                @Param("rateType") String rateType,
                @Param("source") String source,
                @Param("retrievedAt") OffsetDateTime retrievedAt);
}
