package ch.finyoapi.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "News", description = "Financial news from RSS feeds")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "Get financial news from configured RSS sources")
    public ResponseEntity<List<NewsItem>> getNews(
        @RequestParam(required = false) String lang,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(newsService.getNews(lang, Math.min(limit, 100)));
    }
}
