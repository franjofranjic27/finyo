package ch.finyo.transaction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Value key of the duplicate heuristic for statement rows without a bank
 * reference. Used both as the JPQL projection of existing transactions and as
 * the lookup key of the rows being imported, so one {@code HashSet} replaces a
 * query per row.
 *
 * @param date        booking date
 * @param amount      signed amount, normalized because the column has scale 4
 *                    while parsed rows carry scale 2 and {@link BigDecimal#equals}
 *                    is scale-sensitive
 * @param description free-text description, may be null
 */
public record TransactionFingerprint(LocalDate date, BigDecimal amount, @Nullable String description) {

    public TransactionFingerprint {
        amount = amount.stripTrailingZeros();
    }

    static TransactionFingerprint of(DuplicateCandidate row) {
        return new TransactionFingerprint(row.date(), row.amount(), row.description());
    }
}
