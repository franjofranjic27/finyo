package ch.finyo.analytics;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Spending analytics and financial summaries")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserContextProvider userContextProvider;

    @GetMapping("/summary")
    @Operation(summary = "Get spending summary for a time range")
    @ApiResponse(responseCode = "200", description = "Summary returned successfully")
    public ResponseEntity<SpendingSummaryResponse> getSummary(
            @RequestParam(defaultValue = "LAST_30_DAYS") TimeRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/analytics/summary user={} range={}", userId, range);
        return ResponseEntity.ok(analyticsService.getSummary(userId, range, from, to));
    }

    @GetMapping("/by-category")
    @Operation(summary = "Get spending breakdown by category")
    @ApiResponse(responseCode = "200", description = "Category breakdown returned successfully")
    public ResponseEntity<List<CategoryBreakdownItem>> getByCategory(
            @RequestParam(defaultValue = "LAST_30_DAYS") TimeRange range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/analytics/by-category user={} range={}", userId, range);
        return ResponseEntity.ok(analyticsService.getCategoryBreakdown(userId, range, from, to));
    }

    @GetMapping("/monthly")
    @Operation(summary = "Get monthly income/expense overview")
    @ApiResponse(responseCode = "200", description = "Monthly overview returned successfully")
    public ResponseEntity<List<MonthlyDataPoint>> getMonthly(
            @RequestParam(defaultValue = "LAST_12_MONTHS") TimeRange range
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/analytics/monthly user={} range={}", userId, range);
        return ResponseEntity.ok(analyticsService.getMonthlyOverview(userId, range));
    }
}
