package ch.finyo.fx;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The FX providers and where to reach them.
 *
 * There is no fallback chain here, unlike {@code marketdata}: the two providers yield different
 * <em>kinds</em> of rate (mid vs. official), not competing answers to the same question, so there
 * is nothing to fall back between. Which one runs is decided by its {@code enabled} flag alone —
 * each adapter is {@code @ConditionalOnProperty} on it, so a disabled provider has no bean at all.
 *
 * @param frankfurter ECB mid rates; self-hosted in compose, so the default base URL is the public
 *                    instance for a local run without Docker
 * @param bazg        the official Swiss sell rate — one day per request, no range
 */
@ConfigurationProperties(prefix = "finyo.fx")
public record FxProperties(Vendor frankfurter, Vendor bazg) {

    public record Vendor(boolean enabled, String baseUrl) {}
}
