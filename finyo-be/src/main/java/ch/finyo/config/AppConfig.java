package ch.finyo.config;

import ch.finyo.investment.SixMarketDataProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SixMarketDataProperties.class)
public class AppConfig {
}
