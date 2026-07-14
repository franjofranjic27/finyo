package ch.finyo.config;

import ch.finyo.investment.SixMarketDataProperties;
import ch.finyo.marketdata.MarketDataProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// SixMarketDataProperties still backs the legacy SixMarketDataClient (prices).
// PR 2 deletes both once SixQuoteAdapter takes over the quote path.
@Configuration
@EnableConfigurationProperties({SixMarketDataProperties.class, MarketDataProperties.class})
public class AppConfig {
}
