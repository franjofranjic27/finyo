package ch.finyo.config;

import ch.finyo.investment.SixMarketDataProperties;
import ch.finyo.taxdocument.FolderConventionProperties;
import ch.finyo.taxdocument.GraphProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SixMarketDataProperties.class,
        GraphProperties.class,
        FolderConventionProperties.class
})
public class AppConfig {
}
