package ch.finyo.investment;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
@Validated
@Tag(name = "Portfolio", description = "Manage portfolio positions and track their performance")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the aggregated portfolio",
            description = "Returns all positions with current prices (live from SIX where available), "
                    + "totals and allocation. Also upserts today's portfolio snapshot for the history chart.")
    @ApiResponse(responseCode = "200", description = "Portfolio returned successfully")
    public ResponseEntity<PortfolioResponse> getPortfolio() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/portfolio user={}", userId);
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }

    @GetMapping("/history")
    @Operation(summary = "Get the portfolio value history",
            description = "Returns daily snapshots of total value and cost for the given number of months back.")
    @ApiResponse(responseCode = "200", description = "History returned successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<PortfolioHistoryResponse> getHistory(
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int months) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/portfolio/history months={} user={}", months, userId);
        return ResponseEntity.ok(portfolioService.getHistory(userId, months));
    }
}
