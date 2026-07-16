package ch.finyo.marketdata;

import ch.finyo.marketdata.spi.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists a quote, in its own transaction, and never lets the failure out.
 *
 * The same shape as {@link SecurityReferenceCache} and for the same reason: this write can be
 * triggered from inside a user's request (creating a position prices it straight away), and a
 * failure to store a price must never take that request down with it. The user's position is
 * the point; the cached price is a convenience.
 *
 * A {@code TransactionTemplate} rather than {@code @Transactional}, deliberately. With the
 * annotation the catch block sits <em>inside</em> the transaction boundary, so a failed write
 * marks the transaction rollback-only, the catch swallows the exception, and the interceptor
 * then throws {@code UnexpectedRollbackException} on commit — straight past the catch that was
 * supposed to contain it. This bug was real, and a test caught it.
 */
@Slf4j
@Component
public class InstrumentPriceWriter {

    private final InstrumentPriceRepository repository;
    private final TransactionTemplate write;

    public InstrumentPriceWriter(InstrumentPriceRepository repository,
                                 PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.write = new TransactionTemplate(transactionManager);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void store(Quote quote) {
        // A price with no trading day cannot be filed in a time series, a price with no currency
        // is a number without a meaning, and a price of zero or less is not a price at all — SIX
        // returns 0 for a listed-but-never-traded instrument. None is written rather than guessed
        // at. The adapter already rejects these, but a writer that trusts its callers is one
        // refactor away from persisting a 0; this is the second line, and it is cheap.
        if (quote.asOf() == null || quote.currency() == null
                || quote.price() == null || quote.price().signum() <= 0) {
            log.warn("Refusing to store a meaningless quote for {} (price={}, currency={}, asOf={})",
                    quote.isin(), quote.price(), quote.currency(), quote.asOf());
            return;
        }
        try {
            write.executeWithoutResult(_ -> repository.upsert(
                    quote.isin(),
                    quote.asOf(),
                    quote.price(),
                    quote.currency().value(),
                    quote.source().name(),
                    quote.retrievedAt()));
            log.debug("Stored price {} {} for {} as of {}",
                    quote.price(), quote.currency(), quote.isin(), quote.asOf());
        } catch (DataAccessException | TransactionException e) {
            log.warn("Could not store price for {}: {}", quote.isin(), e.getMessage());
        }
    }
}
