package ch.finyo.fx;

import ch.finyo.common.money.CurrencyCode;

import java.util.Collection;

/**
 * Lets a feature module ask for currencies' rates without knowing how syncing works.
 *
 * The counterpart to {@link HeldCurrenciesQuery}: that port lets fx ask investment what is held,
 * this one lets investment tell fx that something new is held <em>right now</em> — a position in a
 * never-seen foreign currency would otherwise show no CHF value until the nightly sync.
 */
public interface FxRateCatchUp {

    /**
     * Fetches rates for any of the given currencies that have none yet. A collection rather than
     * one currency so a bulk import reports its whole batch in a single call — every missing
     * currency then lands in one sync run instead of N triggers racing each other for the sync
     * lock. Fire-and-forget: returns immediately, runs asynchronously, and swallows every
     * failure — a dead rate provider must never break the position creation that triggered it.
     * CHF, {@code null} entries, duplicates and currencies that already have a MID rate are
     * ignored.
     */
    void ensureRates(Collection<CurrencyCode> currencies);
}
