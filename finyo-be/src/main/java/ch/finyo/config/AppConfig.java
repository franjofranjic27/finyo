package ch.finyo.config;

import ch.finyo.marketdata.MarketDataProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class AppConfig {
}
