package ch.finyo.config;

import ch.finyo.fx.FxProperties;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.taxdocument.FolderConventionProperties;
import ch.finyo.taxdocument.GraphProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// SixMarketDataProperties is gone — the legacy SIX client it backed was deleted with the move to
// stored quotes; MarketDataProperties replaces it.
@Configuration
@EnableConfigurationProperties({
        MarketDataProperties.class,
        FxProperties.class,
        GraphProperties.class,
        FolderConventionProperties.class
})
public class AppConfig {
}
