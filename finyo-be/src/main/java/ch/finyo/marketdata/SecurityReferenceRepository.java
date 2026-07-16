package ch.finyo.marketdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface SecurityReferenceRepository extends JpaRepository<CachedSecurityReference, String> {

    Optional<CachedSecurityReference> findFirstByValor(String valor);

    /**
     * Native upsert rather than {@code save()}.
     *
     * Two reasons, both real. Firstly, the entity has an assigned natural key, so Spring
     * Data considers it non-new and {@code save()} degrades to {@code em.merge()} — an
     * extra SELECT before every write. Secondly and more importantly, two concurrent
     * imports of the same new ISIN both miss the cache and both insert; with
     * {@code save()} one of them hits the primary key and blows up. ON CONFLICT makes
     * that race a non-event — the loser's row is identical to the winner's.
     */
    @Modifying
    @Query(value = """
            INSERT INTO security_reference (isin, valor, ticker, name, type, currency, issuer, source, retrieved_at)
            VALUES (:isin, :valor, :ticker, :name, :type, :currency, :issuer, :source, :retrievedAt)
            ON CONFLICT (isin) DO UPDATE SET
                valor        = EXCLUDED.valor,
                ticker       = EXCLUDED.ticker,
                name         = EXCLUDED.name,
                type         = EXCLUDED.type,
                currency     = EXCLUDED.currency,
                issuer       = EXCLUDED.issuer,
                source       = EXCLUDED.source,
                retrieved_at = EXCLUDED.retrieved_at
            """, nativeQuery = true)
    void upsert(@Param("isin") String isin,
                @Param("valor") String valor,
                @Param("ticker") String ticker,
                @Param("name") String name,
                @Param("type") String type,
                @Param("currency") String currency,
                @Param("issuer") String issuer,
                @Param("source") String source,
                @Param("retrievedAt") OffsetDateTime retrievedAt);
}
