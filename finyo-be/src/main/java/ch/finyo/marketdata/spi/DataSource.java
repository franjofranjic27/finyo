package ch.finyo.marketdata.spi;

/**
 * Where a piece of market data came from.
 *
 * Every externally derived value carries this. The reason is a bug this codebase
 * already had: when SIX was unreachable the portfolio silently fell back to the
 * purchase price, so gain/loss read 0.00 and nothing told the user the number was
 * not a market price. Provenance is the fix — not more retries.
 */
public enum DataSource {
    /** SIX Swiss Exchange (FQS). Personal use only — see docs/DATENQUELLEN.md. */
    SIX,
    /** OpenFIGI (Bloomberg). MIT licence, redistribution explicitly permitted. */
    OPENFIGI,
    /** EODHD — the paid fallback, wired but not enabled. */
    EODHD,
    /** Entered by the user. */
    MANUAL,
    /**
     * No provider knows this security, so the asset class was guessed from its name by
     * AssetClassifier. A durable answer, and the normal one for unlisted 3a funds — but
     * the weakest source, and worth showing to the user.
     */
    HEURISTIC,
    /**
     * The providers could not be reached when this instrument was created, so nothing
     * about it has been verified. Distinct from HEURISTIC on purpose: HEURISTIC means
     * "we asked and nobody knew", UNRESOLVED means "we never got to ask". The first is
     * final, the second is a to-do — {@code PositionService} retries it on the next
     * touch, so a provider outage does not permanently freeze a guess into the database.
     */
    UNRESOLVED
}
