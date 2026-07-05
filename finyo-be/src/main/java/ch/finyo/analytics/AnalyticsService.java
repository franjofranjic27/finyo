package ch.finyo.analytics;

import ch.finyo.category.CategoryRepository;
import ch.finyo.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    public SpendingSummaryResponse getSummary(String userId, TimeRange range, LocalDate customFrom, LocalDate customTo) {
        LocalDate from = customFrom != null ? customFrom : range.startDate();
        LocalDate to = customTo != null ? customTo : range.endDate();

        log.debug("Computing spending summary for user={} from={} to={}", userId, from, to);

        var transactions = transactionRepository.findByUserIdAndDateBetweenOrderByDateDesc(userId, from, to);

        BigDecimal totalIncome = transactions.stream()
                .map(t -> t.getAmount())
                .filter(a -> a.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses = transactions.stream()
                .map(t -> t.getAmount())
                .filter(a -> a.compareTo(BigDecimal.ZERO) < 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        return new SpendingSummaryResponse(
                totalExpenses,
                totalIncome,
                totalIncome.subtract(totalExpenses),
                from,
                to,
                transactions.size()
        );
    }

    public List<CategoryBreakdownItem> getCategoryBreakdown(String userId, TimeRange range, LocalDate customFrom, LocalDate customTo) {
        LocalDate from = customFrom != null ? customFrom : range.startDate();
        LocalDate to = customTo != null ? customTo : range.endDate();

        log.debug("Computing category breakdown for user={} from={} to={}", userId, from, to);

        List<Object[]> rawData = transactionRepository.sumExpensesByCategoryForPeriod(userId, from, to);

        BigDecimal totalExpenses = rawData.stream()
                .map(row -> ((BigDecimal) row[1]).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rawData.stream().map(row -> {
            UUID categoryId = (UUID) row[0];
            BigDecimal total = ((BigDecimal) row[1]).abs();
            double percentage = totalExpenses.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : total.divide(totalExpenses, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

            var category = categoryRepository.findById(categoryId).orElse(null);
            String categoryName = category != null ? category.getName() : "Uncategorized";
            String categoryColor = category != null ? category.getColor() : null;

            return new CategoryBreakdownItem(categoryId, categoryName, categoryColor, total, percentage);
        })
        .sorted(Comparator.comparing(CategoryBreakdownItem::total).reversed())
        .toList();
    }

    public List<MonthlyDataPoint> getMonthlyOverview(String userId, TimeRange range) {
        LocalDate from = range.startDate();
        LocalDate to = range.endDate();

        log.debug("Computing monthly overview for user={} from={} to={}", userId, from, to);

        List<Object[]> rawData = transactionRepository.monthlyTotalsForPeriod(userId, from, to);

        return rawData.stream().map(row -> {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal expenses = row[2] != null ? ((BigDecimal) row[2]).abs() : BigDecimal.ZERO;
            BigDecimal income = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;
            return new MonthlyDataPoint(year, month, expenses, income, income.subtract(expenses));
        }).toList();
    }
}
