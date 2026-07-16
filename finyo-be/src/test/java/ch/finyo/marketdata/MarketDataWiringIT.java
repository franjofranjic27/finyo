package ch.finyo.marketdata;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.marketdata.spi.SecurityReferenceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application with the market-data providers <em>enabled</em> and asserts that
 * the whole chain wires up.
 *
 * This exists because of a false green. Every other test runs with
 * {@code reference-providers: []} so that nothing reaches the network — which also means
 * the adapters are never instantiated, and a broken constructor dependency is invisible.
 * That is exactly what happened: the adapters were switched to the auto-configured
 * {@code RestClient.Builder} (the only one that honours the HTTP timeouts), but Boot 4
 * modularised the HTTP clients and {@code spring-boot-starter-webmvc} does not ship
 * {@code RestClientAutoConfiguration}. The bean did not exist. The full suite stayed green
 * and production would have failed to start.
 *
 * No network traffic happens here: the beans are constructed at startup, but nobody calls
 * them. The base URLs point at a dead port anyway, so a regression that fires an HTTP
 * request during construction fails loudly instead of quietly reaching out to SIX.
 */
@TestPropertySource(properties = {
        "finyo.marketdata.reference-providers=six,openfigi",
        "finyo.marketdata.six.enabled=true",
        "finyo.marketdata.six.base-url=http://127.0.0.1:1/fqs",
        "finyo.marketdata.openfigi.enabled=true",
        "finyo.marketdata.openfigi.base-url=http://127.0.0.1:1"
})
class MarketDataWiringIT extends BaseIntegrationTest {

    @Autowired
    private List<SecurityReferenceProvider> providers;

    @Autowired
    private SecurityLookup securityLookup;

    @Test
    @DisplayName("the provider chain wires up when the providers are actually enabled")
    void bothProvidersAreWired() {
        assertThat(securityLookup).isNotNull();
        assertThat(providers)
                .extracting(SecurityReferenceProvider::name)
                .containsExactlyInAnyOrder("six", "openfigi");
    }
}
