package ch.finyo.budget;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/monthly-budget")
@RequiredArgsConstructor
@Tag(name = "Monthly Budget", description = "Per-user monthly budget plan")
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the monthly budget plan; returns zeros plus current fixed costs when none is set")
    @ApiResponse(responseCode = "200", description = "Monthly budget returned successfully")
    public ResponseEntity<MonthlyBudgetResponse> get() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/monthly-budget user={}", userId);
        return ResponseEntity.ok(monthlyBudgetService.get(userId));
    }

    @PutMapping
    @Operation(summary = "Create or update the monthly budget plan (upsert)")
    @ApiResponse(responseCode = "200", description = "Monthly budget saved")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<MonthlyBudgetResponse> upsert(@Valid @RequestBody MonthlyBudgetRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/monthly-budget user={}", userId);
        return ResponseEntity.ok(monthlyBudgetService.upsert(request, userId));
    }
}
