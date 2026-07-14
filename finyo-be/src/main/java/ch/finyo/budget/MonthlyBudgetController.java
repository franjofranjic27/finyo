package ch.finyo.budget;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/monthly-budget")
@RequiredArgsConstructor
@Tag(name = "Monthly Budget", description = "Per-user monthly budget plan with dynamic allocation positions")
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the monthly budget plan; returns a zero net income plus current fixed costs when none is set")
    @ApiResponse(responseCode = "200", description = "Monthly budget returned successfully")
    public ResponseEntity<MonthlyBudgetResponse> get() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/monthly-budget user={}", userId);
        return ResponseEntity.ok(monthlyBudgetService.get(userId));
    }

    @PutMapping
    @Operation(summary = "Create or update the net income of the monthly budget plan (upsert)")
    @ApiResponse(responseCode = "200", description = "Monthly budget saved")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<MonthlyBudgetResponse> upsert(@Valid @RequestBody MonthlyBudgetRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/monthly-budget user={}", userId);
        return ResponseEntity.ok(monthlyBudgetService.upsert(request, userId));
    }

    @PostMapping("/positions")
    @Operation(summary = "Create a new budget position; returns the full rebuilt budget plan")
    @ApiResponse(responseCode = "201", description = "Budget position created")
    @ApiResponse(responseCode = "400", description = "Validation failed or the name already exists")
    public ResponseEntity<MonthlyBudgetResponse> createPosition(
            @Valid @RequestBody MonthlyBudgetPositionRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/monthly-budget/positions user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(monthlyBudgetService.createPosition(request, userId));
    }

    @PutMapping("/positions/{id}")
    @Operation(summary = "Update a budget position; returns the full rebuilt budget plan")
    @ApiResponse(responseCode = "200", description = "Budget position updated")
    @ApiResponse(responseCode = "400", description = "Validation failed or the name already exists")
    @ApiResponse(responseCode = "404", description = "Budget position not found")
    public ResponseEntity<MonthlyBudgetResponse> updatePosition(
            @PathVariable UUID id, @Valid @RequestBody MonthlyBudgetPositionRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/monthly-budget/positions/{} user={}", id, userId);
        return ResponseEntity.ok(monthlyBudgetService.updatePosition(id, request, userId));
    }

    @DeleteMapping("/positions/{id}")
    @Operation(summary = "Delete a budget position; returns the full rebuilt budget plan")
    @ApiResponse(responseCode = "200", description = "Budget position deleted")
    @ApiResponse(responseCode = "404", description = "Budget position not found")
    public ResponseEntity<MonthlyBudgetResponse> deletePosition(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/monthly-budget/positions/{} user={}", id, userId);
        return ResponseEntity.ok(monthlyBudgetService.deletePosition(id, userId));
    }
}
