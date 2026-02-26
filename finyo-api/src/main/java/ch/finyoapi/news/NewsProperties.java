package ch.finyoapi.news;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "finyo.news")
public record NewsProperties(
    List<FeedConfig> feeds
) {
    public record FeedConfig(
        String name,
        String url,
        String language
    ) {}
}
