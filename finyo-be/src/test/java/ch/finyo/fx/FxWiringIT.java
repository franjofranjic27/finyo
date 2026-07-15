package ch.finyo.fx;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.fx.spi.FxRateProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application with the FX providers <em>enabled</em> and asserts the beans wire up.
 *
 * The same guard as {@link ch.finyo.marketdata.MarketDataWiringIT}, and for the same reason: every
 * other test runs with the FX providers off so nothing reaches Frankfurter or BAZG — which also
 * means the adapters are never constructed, and a broken constructor dependency (a missing
 * {@code RestClient.Builder}, a mistyped properties record) would stay invisible while the suite
 * went green and the real application failed to start.
 *
 * No traffic happens: the beans are built at startup and nobody calls them, and the base URLs
 * point at a dead port so a regression that fires an HTTP request during construction fails loudly.
 */
@TestPropertySource(properties = {
        "finyo.fx.frankfurter.enabled=true",
        "finyo.fx.frankfurter.base-url=http://127.0.0.1:1",
        "finyo.fx.bazg.enabled=true",
        "finyo.fx.bazg.base-url=http://127.0.0.1:1"
})
class FxWiringIT extends BaseIntegrationTest {

    @Autowired
    private List<FxRateProvider> providers;

    @Autowired
    private FxConverter fxConverter;

    @Test
    @DisplayName("both FX providers wire up when they are actually enabled")
    void bothFxProvidersAreWired() {
        assertThat(fxConverter).isNotNull();
        assertThat(providers)
                .extracting(FxRateProvider::name)
                .containsExactlyInAnyOrder("frankfurter", "bazg");
    }
}
