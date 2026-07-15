package ch.finyo.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InstrumentPriceRepository extends JpaRepository<InstrumentPrice, InstrumentPrice.Key> {

    Optional<InstrumentPrice> findFirstByIsinOrderByPriceDateDesc(String isin);

    long countByIsin(String isin);

    /** One query for the whole portfolio: the latest price per ISIN, without an N+1. */
    @Query("""
            SELECT p FROM InstrumentPrice p
            WHERE p.isin IN :isins
              AND p.priceDate = (SELECT MAX(q.priceDate) FROM InstrumentPrice q WHERE q.isin = p.isin)
            """)
    List<InstrumentPrice> findLatestForEach(@Param("isins") Collection<String> isins);

    List<InstrumentPrice> findByIsinAndPriceDateGreaterThanEqualOrderByPriceDateAsc(String isin, LocalDate from);

    /**
     * Same ISIN and day arriving twice — a nightly sync racing a manual admin trigger, or a
     * re-run after a partial failure — must be an update, not a crash. The price is a fact
     * about the market: the later read of it simply wins.
     */
    @Modifying
    @Query(value = """
            INSERT INTO instrument_price (isin, price_date, close, currency, source, retrieved_at)
            VALUES (:isin, :priceDate, :close, :currency, :source, :retrievedAt)
            ON CONFLICT (isin, price_date) DO UPDATE SET
                close        = EXCLUDED.close,
                currency     = EXCLUDED.currency,
                source       = EXCLUDED.source,
                retrieved_at = EXCLUDED.retrieved_at
            """, nativeQuery = true)
    void upsert(@Param("isin") String isin,
                @Param("priceDate") LocalDate priceDate,
                @Param("close") BigDecimal close,
                @Param("currency") String currency,
                @Param("source") String source,
                @Param("retrievedAt") OffsetDateTime retrievedAt);
}
