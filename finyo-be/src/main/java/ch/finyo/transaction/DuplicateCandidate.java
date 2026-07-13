package ch.finyo.transaction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The slice of an import row the duplicate detection needs — implemented by the
 * parsed preview rows and by {@link ImportCommitRow}, so preview and commit
 * share one batch lookup.
 */
interface DuplicateCandidate {

    LocalDate date();

    BigDecimal amount();

    @Nullable String description();

    /** Bank-side reference; when present it alone decides whether the row is a duplicate. */
    @Nullable String externalRef();
}
