package ch.finyoapi.news;

import java.time.OffsetDateTime;

public record NewsItem(
    String title,
    String link,
    String description,
    String source,
    String language,
    OffsetDateTime publishedAt
) {}
