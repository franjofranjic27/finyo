package ch.finyo.fx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists one exchange rate, in its own transaction, and never lets the failure out.
 *
 * The same shape as {@link ch.finyo.marketdata.InstrumentPriceWriter}, and for the same two
 * reasons. A {@code TransactionTemplate} with {@code REQUIRES_NEW} rather than {@code @Transactional}:
 * with the annotation the catch would sit inside the transaction boundary, a failed write would
 * mark it rollback-only, and the interceptor would then throw {@code UnexpectedRollbackException}
 * on commit — straight past the catch meant to contain it. And a guarded upsert, so one bad rate in
 * a backfill of hundreds does not abort the run.
 */
@Slf4j
@Component
public class FxRateWriter {

    private final FxRateRepository repository;
    private final TransactionTemplate write;

    public FxRateWriter(FxRateRepository repository,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.write = new TransactionTemplate(transactionManager);
        this.write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void store(FxRate rate) {
        // A rate with no day cannot be filed in a time series, and a rate of zero or less is not a
        // rate — it would divide a portfolio value to nothing or flip its sign. The adapters
        // already reject these; this is the cheap second line before it reaches the database.
        if (rate.getRateDate() == null || rate.getCurrency() == null
                || rate.getChfPerUnit() == null || rate.getChfPerUnit().signum() <= 0) {
            log.warn("Refusing to store a meaningless fx rate: {} {} = {} on {}",
                    rate.getRateType(), rate.getCurrency(), rate.getChfPerUnit(), rate.getRateDate());
            return;
        }
        try {
            write.executeWithoutResult(_ -> repository.upsert(
                    rate.getCurrency(),
                    rate.getRateDate(),
                    rate.getChfPerUnit(),
                    rate.getRateType().name(),
                    rate.getSource(),
                    rate.getRetrievedAt()));
            log.debug("Stored fx rate {} {} = {} CHF on {}",
                    rate.getRateType(), rate.getCurrency(), rate.getChfPerUnit(), rate.getRateDate());
        } catch (DataAccessException | TransactionException e) {
            log.warn("Could not store fx rate for {} {}: {}",
                    rate.getRateType(), rate.getCurrency(), e.getMessage());
        }
    }
}
