package ch.finyo.marketdata;

import java.util.List;

/**
 * Which securities anyone actually holds.
 *
 * A port, not a repository call, because the answer lives in {@code investment} and
 * {@code marketdata} must not depend on a feature module — market facts are consumed by
 * investment, wealth and tax, so the dependency has to run the other way. This inverts it: the
 * job asks the question, the investment module answers it.
 *
 * It exists so the nightly sync prices a few dozen securities rather than a universe.
 */
public interface HeldIsinsQuery {

    /** Distinct, non-null ISINs across all users. Tenant-free, like the prices themselves. */
    List<String> findAll();
}
