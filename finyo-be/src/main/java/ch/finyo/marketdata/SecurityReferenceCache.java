package ch.finyo.marketdata;

import ch.finyo.marketdata.spi.SecurityReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes resolved master data to the persistent cache.
 *
 * Its own component, and its own transaction, for one reason: <b>a cache write must
 * never be able to fail the user's actual operation.</b> Sharing the caller's
 * transaction would mean that two people importing the same new ISIN at the same time
 * both miss the cache, both insert, one loses the primary-key race, and their perfectly
 * valid position create dies with a 409 — over a write that was pure optimisation.
 *
 * REQUIRES_NEW isolates the failure, and the upsert makes the race a non-event: the
 * loser's data is identical to the winner's anyway.
 *
 * <p><b>The transaction is a TransactionTemplate and not {@code @Transactional}, and
 * that is the whole point of the class working at all.</b> With the annotation, the
 * try/catch would sit <em>inside</em> the transaction boundary: when the upsert fails,
 * Hibernate marks the transaction rollback-only, the catch block swallows the exception,
 * the method returns normally — and the transaction interceptor then throws
 * {@code UnexpectedRollbackException} on commit, straight into the caller. The failure
 * the catch block exists to contain would escape past it, and the user's position create
 * would die on a caching detail after all. A template puts the boundary inside the try,
 * where it can actually be caught.
 */
@Slf4j
@Component
public class SecurityReferenceCache {

    /** Vendor strings are not bound by our column widths. */
    private static final int MAX_TEXT_LENGTH = 255;

    private final SecurityReferenceRepository repository;
    private final TransactionTemplate cacheWrite;

    public SecurityReferenceCache(SecurityReferenceRepository repository,
                                  PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.cacheWrite = new TransactionTemplate(transactionManager);
        this.cacheWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void store(SecurityReference reference) {
        // No ISIN means no primary key. The reference is still perfectly usable — it just
        // cannot be cached.
        if (reference.isin() == null || reference.isin().isBlank()) {
            return;
        }
        try {
            cacheWrite.executeWithoutResult(_ -> upsert(reference));
            log.info("Cached security reference isin={} source={}", reference.isin(), reference.source());
        } catch (DataAccessException | TransactionException e) {
            // Caching is best-effort by definition. The caller already has the answer, and
            // must not learn about this at all.
            log.warn("Could not cache security reference isin={}: {}", reference.isin(), e.getMessage());
        }
    }

    private void upsert(SecurityReference reference) {
        repository.upsert(
                reference.isin(),
                reference.valor(),
                reference.ticker(),
                truncate(reference.name()),
                reference.type().name(),
                reference.currency() == null ? null : reference.currency().value(),
                truncate(reference.issuer()),
                reference.source().name(),
                reference.retrievedAt());
    }

    /** A long issuer name must not fail the write. */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_TEXT_LENGTH ? value : value.substring(0, MAX_TEXT_LENGTH);
    }
}
