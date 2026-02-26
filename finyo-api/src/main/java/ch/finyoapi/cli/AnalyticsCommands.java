package ch.finyoapi.cli;

import ch.finyoapi.analytics.AnalyticsService;
import ch.finyoapi.analytics.TimeRange;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
@RequiredArgsConstructor
public class AnalyticsCommands {

    private final AnalyticsService analyticsService;
    private final CliUserContext cliUserContext;

    @ShellMethod(key = "analytics summary", value = "Show spending summary for a time range")
    public String summary(
            @ShellOption(defaultValue = "LAST_30_DAYS",
                         help = "Time range: LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_6_MONTHS, LAST_12_MONTHS")
            TimeRange range
    ) {
        var result = analyticsService.getSummary(cliUserContext.getUserId(), range, null, null);
        return String.format(
                "╔══════════════════════════════════════╗%n" +
                "║  Spending Summary (%s)%n" +
                "╠══════════════════════════════════════╣%n" +
                "║  Period:       %s to %s%n" +
                "║  Income:       CHF %s%n" +
                "║  Expenses:     CHF %s%n" +
                "║  Net:          CHF %s%n" +
                "║  Transactions: %d%n" +
                "╚══════════════════════════════════════╝",
                range,
                result.rangeStart(), result.rangeEnd(),
                result.totalIncome().toPlainString(),
                result.totalExpenses().toPlainString(),
                result.netAmount().toPlainString(),
                result.transactionCount()
        );
    }

    @ShellMethod(key = "analytics by-category", value = "Show spending breakdown by category")
    public String byCategory(
            @ShellOption(defaultValue = "LAST_30_DAYS",
                         help = "Time range: LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_6_MONTHS, LAST_12_MONTHS")
            TimeRange range
    ) {
        var items = analyticsService.getCategoryBreakdown(cliUserContext.getUserId(), range, null, null);
        if (items.isEmpty()) {
            return "No expense data for the selected range.";
        }

        StringBuilder sb = new StringBuilder("Category Spending (" + range + ")\n");
        sb.append("─".repeat(52)).append("\n");
        items.forEach(item ->
                sb.append(String.format("%-25s  CHF %10.2f  (%5.1f%%)%n",
                        item.categoryName(), item.total(), item.percentage()))
        );
        return sb.toString();
    }
}
