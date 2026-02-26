package ch.finyoapi.cli;

import ch.finyoapi.news.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class NewsCommands {

    private final NewsService newsService;

    @ShellMethod(key = "news list", value = "Show latest financial news headlines")
    public String list(
            @ShellOption(defaultValue = ShellOption.NULL,
                         help = "Language filter: de, en (default: all languages)")
            String lang,
            @ShellOption(defaultValue = "10",
                         help = "Maximum number of headlines to show")
            int limit
    ) {
        var items = newsService.getNews(lang, limit);
        if (items.isEmpty()) {
            return "No news items found.";
        }

        StringBuilder sb = new StringBuilder("Financial News\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-45s  %-20s  %s%n", "Title", "Source", "Date"));
        sb.append("─".repeat(80)).append("\n");
        items.forEach(item -> {
            String title  = truncate(item.title() != null ? item.title() : "-", 45);
            String source = truncate(item.source() != null ? item.source() : "-", 20);
            String date   = item.publishedAt() != null
                    ? item.publishedAt().toLocalDate().toString()
                    : "-";
            sb.append(String.format("%-45s  %-20s  %s%n", title, source, date));
        });
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
