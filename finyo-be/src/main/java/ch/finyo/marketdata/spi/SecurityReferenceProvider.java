package ch.finyo.marketdata.spi;

/**
 * Resolves master data for a security. Implemented by the adapters in
 * {@code ch.finyo.integration}.
 *
 * The port is split by capability rather than bundling lookup and quotes into one
 * interface, because the providers are asymmetric: SIX can do both, OpenFIGI only
 * master data. A single fat interface would force OpenFIGI to answer
 * {@code latestQuote()} with {@code Optional.empty()} — an ISP violation that
 * disguises itself as "the provider just returned nothing" and poisons debugging.
 */
public interface SecurityReferenceProvider {

    /** Configuration key of this provider, e.g. {@code "six"}. Matches finyo.marketdata.reference-providers. */
    String name();

    /**
     * Whether this provider can resolve that kind of identifier at all.
     * OpenFIGI, for instance, has no concept of a Swiss valor number — asking it
     * would burn a rate-limited request on a guaranteed miss.
     */
    boolean supports(SecurityId id);

    /**
     * Never throws for an unreachable backend — an unavailable source is an expected
     * state, not an exception. It is reported as {@link LookupResult.Unavailable},
     * which is deliberately <em>not</em> the same as {@link LookupResult.NotFound}:
     * only the latter licenses the caller to write a fallback down as fact.
     */
    LookupResult lookup(SecurityId id);
}
