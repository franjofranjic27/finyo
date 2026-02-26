package ch.finyoapi.investment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finyo.six")
public record SixMarketDataProperties(
        String baseUrl,
        String apiKey
) {}
