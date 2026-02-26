package ch.finyoapi.news;

import ch.finyoapi.config.CacheConfig;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsProperties newsProperties;

    @Cacheable(CacheConfig.CACHE_NEWS)
    public List<NewsItem> getNews(String language, int limit) {
        List<NewsItem> allItems = new ArrayList<>();

        for (NewsProperties.FeedConfig feed : newsProperties.feeds()) {
            if (language != null && !language.isBlank() && !language.equalsIgnoreCase(feed.language())) {
                continue;
            }
            try {
                List<NewsItem> feedItems = fetchFeed(feed);
                allItems.addAll(feedItems);
            } catch (Exception e) {
                log.warn("Failed to fetch RSS feed '{}' from {}: {}", feed.name(), feed.url(), e.getMessage());
            }
        }

        return allItems.stream()
            .sorted(Comparator.comparing(NewsItem::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(limit > 0 ? limit : 20)
            .toList();
    }

    private List<NewsItem> fetchFeed(NewsProperties.FeedConfig feedConfig) throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(new URL(feedConfig.url())));

        return feed.getEntries().stream().map(entry -> new NewsItem(
            entry.getTitle(),
            entry.getLink(),
            extractDescription(entry),
            feedConfig.name(),
            feedConfig.language(),
            entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().atOffset(ZoneOffset.UTC)
                : null
        )).toList();
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() != null) {
            String desc = entry.getDescription().getValue();
            if (desc != null) {
                desc = desc.replaceAll("<[^>]+>", "").trim();
                if (desc.length() > 300) desc = desc.substring(0, 297) + "...";
            }
            return desc;
        }
        return null;
    }
}
