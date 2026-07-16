package ch.finyo.marketdata.spi;

import ch.finyo.common.SourceResult;

/**
 * Fetches the current price of a security. Implemented by the adapters in
 * {@code ch.finyo.integration}.
 *
 * A separate port from {@link SecurityReferenceProvider} rather than more methods on it,
 * because the providers are asymmetric: SIX serves both master data and quotes, OpenFIGI
 * only master data — it is a symbology service and has no prices at all. One fat interface
 * would force OpenFIGI to answer {@code quote()} with "nothing", which reads like "this
 * security has no price" and is a lie.
 *
 * <p>There is no batch method, and that is a finding rather than an omission: SIX FQS
 * evaluates only the first term of a multi-ISIN {@code where} clause and silently returns a
 * single row. Probed against the live endpoint — comma, pipe and IN syntax all fail. So one
 * request per security it is, which is exactly why quotes are fetched by a nightly job over
 * the handful of distinct ISINs a user holds, and never on the read path.
 */
public interface QuoteProvider {

    /** Configuration key of this provider, e.g. {@code "six"}. */
    String name();

    boolean supports(SecurityId id);

    /**
     * {@link SourceResult.NotFound} means the security has no price at this provider — the
     * normal answer for an unlisted 3a fund, and a durable one. {@link SourceResult.Unavailable}
     * means the provider could not be reached and says nothing about the security, so the
     * caller must not write anything down.
     */
    SourceResult<Quote> quote(SecurityId id);
}
