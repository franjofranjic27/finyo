package ch.finyo.marketdata.spi;

import ch.finyo.common.SourceResult;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches a security's daily closing prices back to a given date. Implemented by the adapters
 * in {@code ch.finyo.integration}.
 *
 * Its own port, alongside {@link QuoteProvider} and {@link SecurityReferenceProvider}, because
 * the providers are asymmetric: SIX serves history, OpenFIGI does not. The same reasoning as
 * the other two — a fat interface would force a symbology-only provider to answer "no history"
 * in a way indistinguishable from "this security has none".
 */
public interface PriceHistoryProvider {

    String name();

    boolean supports(SecurityId id);

    /**
     * {@link SourceResult.NotFound} means the provider has no history for this security — the
     * normal answer for an unlisted fund. {@link SourceResult.Unavailable} means it could not be
     * reached, and says nothing about the security. An empty-but-Found list is possible too: the
     * security is known, it simply has no closes in the requested window.
     */
    SourceResult<List<PriceBar>> history(SecurityId id, LocalDate from);
}
