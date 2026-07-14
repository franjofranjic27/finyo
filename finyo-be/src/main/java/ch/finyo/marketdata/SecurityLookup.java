package ch.finyo.marketdata;

import ch.finyo.marketdata.spi.LookupResult;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReferenceProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a security's master data: persistent cache first, then the configured
 * provider chain.
 *
 * Composed rather than implementing {@link SecurityReferenceProvider} itself — a
 * composite that implements the very interface it collects would be injected into its
 * own {@code List<SecurityReferenceProvider>} by Spring, and recurse.
 *
 * <p><b>Not transactional, and that is deliberate.</b> This method makes HTTP calls. If
 * it joined the caller's transaction (which is what {@code @Transactional} would do
 * here — REQUIRED joins, it does not create a boundary), every position created would
 * hold a database connection open across the network round-trip. Ten concurrent creates
 * against a hanging vendor would drain the connection pool and take down endpoints that
 * have nothing to do with investments. The cache write gets its own short transaction
 * instead, in {@link SecurityReferenceCache}.
 *
 * <p>Postgres-first is not a performance tweak, it is the availability property: lookup
 * keeps working when SIX is down, when the circuit breaker is open, and on the day SIX
 * has to be switched off for legal reasons.
 */
@Slf4j
@Service
public class SecurityLookup {

    private final List<SecurityReferenceProvider> chain;
    private final SecurityReferenceRepository repository;
    private final SecurityReferenceCache cache;

    /**
     * Misses are cached too, and they have to be: the unlisted 3a funds are — by this
     * project's own reckoning — the common case, and every one of them costs a round trip
     * to *both* providers before coming back empty. Without this, re-importing the same
     * 3a portfolio hammers two vendors we are merely tolerated on, every single time.
     *
     * Only in memory, and only for an hour: a security that genuinely gets listed should
     * become resolvable again without a redeploy.
     */
    private final Cache<String, Boolean> misses = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public SecurityLookup(List<SecurityReferenceProvider> providers,
                          MarketDataProperties properties,
                          SecurityReferenceRepository repository,
                          SecurityReferenceCache cache) {
        this.repository = repository;
        this.cache = cache;
        this.chain = orderByConfiguration(providers, properties.referenceProviders());
        log.info("Security reference chain: {}", this.chain.stream().map(SecurityReferenceProvider::name).toList());
    }

    public LookupResult resolve(SecurityId id) {
        Optional<CachedSecurityReference> cached = findCached(id);
        if (cached.isPresent()) {
            log.debug("Security reference cache hit for {}", id.value());
            return LookupResult.found(cached.get().toReference());
        }
        if (misses.getIfPresent(id.value()) != null) {
            log.debug("Security reference known-miss for {}", id.value());
            return LookupResult.notFound();
        }

        return askProviders(id);
    }

    /**
     * The first provider with an answer wins. If none has one, the distinction that
     * matters is <em>why</em>: a chain in which somebody was unreachable has not
     * established that the security is unknown — it has established nothing. Reporting
     * that as NotFound would let the caller write a guess down as fact and never ask
     * again.
     */
    private LookupResult askProviders(SecurityId id) {
        boolean anyUnavailable = false;
        String reason = null;

        for (SecurityReferenceProvider provider : chain) {
            if (!provider.supports(id)) {
                continue;
            }
            switch (lookupQuietly(provider, id)) {
                case LookupResult.Found found -> {
                    cache.store(found.reference());
                    return found;
                }
                case LookupResult.Unavailable unavailable -> {
                    anyUnavailable = true;
                    reason = unavailable.reason();
                }
                case LookupResult.NotFound _ -> {
                    // this provider does not know it; the next one might
                }
            }
        }

        if (anyUnavailable) {
            log.warn("Could not resolve {} — a provider was unavailable: {}", id.value(), reason);
            return LookupResult.unavailable(reason);
        }

        log.info("No provider knows {} — this is expected for unlisted funds", id.value());
        misses.put(id.value(), Boolean.TRUE);
        return LookupResult.notFound();
    }

    private Optional<CachedSecurityReference> findCached(SecurityId id) {
        return switch (id) {
            // The ISIN is the primary key and SecurityId.Isin already uppercases it, so
            // this hits the PK index rather than forcing an upper(isin) comparison.
            case SecurityId.Isin isin -> repository.findById(isin.value());
            case SecurityId.Valor valor -> repository.findFirstByValor(valor.value());
        };
    }

    /**
     * A provider that blows up must not take the chain down with it — the next one may
     * well have the answer. Adapters already report the expected failures as Unavailable;
     * this catches the unexpected, and reports it as what it is: a vendor problem, not a
     * statement about the security.
     */
    private LookupResult lookupQuietly(SecurityReferenceProvider provider, SecurityId id) {
        try {
            return provider.lookup(id);
        } catch (RuntimeException e) {
            log.warn("Provider {} failed to resolve {}: {}", provider.name(), id.value(), e.getMessage());
            return LookupResult.unavailable(provider.name() + ": " + e.getClass().getSimpleName());
        }
    }

    /**
     * Configuration order wins; providers absent from the list are dropped even when
     * their bean exists. That way {@code enabled: false} and "not in the chain" cannot
     * disagree.
     */
    private static List<SecurityReferenceProvider> orderByConfiguration(List<SecurityReferenceProvider> providers,
                                                                        List<String> configuredOrder) {
        return providers.stream()
                .filter(provider -> configuredOrder.contains(provider.name()))
                .sorted(Comparator.comparingInt(provider -> configuredOrder.indexOf(provider.name())))
                .toList();
    }
}
