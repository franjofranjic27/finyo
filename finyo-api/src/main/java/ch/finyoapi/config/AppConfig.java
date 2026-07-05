package ch.finyoapi.config;

import ch.finyoapi.investment.SixMarketDataProperties;
import ch.finyoapi.news.NewsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SixMarketDataProperties.class, NewsProperties.class})
public class AppConfig {
}
