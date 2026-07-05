package ch.finyo.analytics;

import java.time.LocalDate;

public enum TimeRange {
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_6_MONTHS,
    LAST_12_MONTHS;

    public LocalDate startDate() {
        LocalDate today = LocalDate.now();
        return switch (this) {
            case LAST_7_DAYS -> today.minusDays(7);
            case LAST_30_DAYS -> today.minusDays(30);
            case THIS_MONTH -> today.withDayOfMonth(1);
            case LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1);
            case LAST_3_MONTHS -> today.minusMonths(3).withDayOfMonth(1);
            case LAST_6_MONTHS -> today.minusMonths(6).withDayOfMonth(1);
            case LAST_12_MONTHS -> today.minusMonths(12).withDayOfMonth(1);
        };
    }

    public LocalDate endDate() {
        LocalDate today = LocalDate.now();
        return switch (this) {
            case LAST_MONTH -> today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());
            default -> today;
        };
    }
}
