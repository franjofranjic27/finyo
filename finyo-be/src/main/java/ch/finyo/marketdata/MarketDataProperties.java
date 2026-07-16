package ch.finyo.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Which providers are active, and in which order they are tried.
 *
 * The chain order lives in configuration rather than in {@code @Order} annotations
 * on purpose: which vendor answers first is an operational decision, not a code
 * one. The day finyo gets a second user — at which point SIX's "exclusively for
 * personal use" terms no longer hold — the switch is three YAML lines:
 *
 * <pre>
 *   six:      { enabled: false }
 *   eodhd:    { enabled: true, api-key: ... }
 *   reference-providers: [eodhd, openfigi]
 * </pre>
 *
 * Spring profiles would be the wrong tool here: they describe environments
 * (dev/prod/test), and provider choice is orthogonal to that — one may well want
 * to try EODHD in dev. Abusing profiles as feature switches multiplies the test
 * matrix.
 *
 * @param referenceProviders provider names in fallback order; providers not listed
 *                           are ignored even when their bean exists
 */
@ConfigurationProperties(prefix = "finyo.marketdata")
public record MarketDataProperties(
        List<String> referenceProviders,
        List<String> quoteProviders,
        List<String> historyProviders,
        SixProperties six,
        OpenFigiProperties openfigi,
        EodhdProperties eodhd
) {

    public MarketDataProperties {
        referenceProviders = referenceProviders == null ? List.of() : List.copyOf(referenceProviders);
        quoteProviders = quoteProviders == null ? List.of() : List.copyOf(quoteProviders);
        historyProviders = historyProviders == null ? List.of() : List.copyOf(historyProviders);
    }

    public record SixProperties(boolean enabled, String baseUrl) {}

    // The generated toString() of a record prints every component, so an api key would go
    // out in plain text on any log line, binding error or stack trace that happens to carry
    // the properties object. Masked here rather than relied upon not to happen.
    public record OpenFigiProperties(boolean enabled, String baseUrl, String apiKey) {
        @Override
        public String toString() {
            return "OpenFigiProperties[enabled=%s, baseUrl=%s, apiKey=%s]"
                    .formatted(enabled, baseUrl, mask(apiKey));
        }
    }

    public record EodhdProperties(boolean enabled, String baseUrl, String apiKey) {
        @Override
        public String toString() {
            return "EodhdProperties[enabled=%s, baseUrl=%s, apiKey=%s]"
                    .formatted(enabled, baseUrl, mask(apiKey));
        }
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "(none)" : "***";
    }
}
