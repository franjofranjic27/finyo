package ch.finyo.fx;

import ch.finyo.common.money.CurrencyCode;

import java.util.List;

/**
 * Which foreign currencies anyone actually holds.
 *
 * A port, not a repository call, because the answer lives in {@code investment} and {@code fx} must
 * not depend on a feature module — rates are consumed by investment, wealth and tax, so the
 * dependency runs the other way. The sync job asks the question; the investment module answers it.
 *
 * It exists so the nightly sync fetches a rate for the two or three currencies actually needed,
 * not for every currency in the world.
 */
public interface HeldCurrenciesQuery {

    /** Distinct non-CHF currencies across all instruments. Tenant-free, like the rates themselves. */
    List<CurrencyCode> findForeign();
}
