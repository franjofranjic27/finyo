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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/budgets")
@RequiredArgsConstructor
@Tag(name = "Budgets", description = "Manage spending budgets per category")
public class BudgetController {

    private final BudgetService budgetService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all budgets for the current user")
    @ApiResponse(responseCode = "200", description = "Budgets returned successfully")
    public ResponseEntity<List<BudgetResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/budgets user={}", userId);
        return ResponseEntity.ok(budgetService.getAll(userId));
    }

    @GetMapping("/status")
    @Operation(summary = "Get current month budget status with spending amounts")
    @ApiResponse(responseCode = "200", description = "Budget status returned successfully")
    public ResponseEntity<List<BudgetStatusResponse>> getCurrentStatus() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/budgets/status user={}", userId);
        return ResponseEntity.ok(budgetService.getCurrentStatus(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get budget by ID")
    @ApiResponse(responseCode = "200", description = "Budget found")
    @ApiResponse(responseCode = "404", description = "Budget not found")
    public ResponseEntity<BudgetResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/budgets/{} user={}", id, userId);
        return ResponseEntity.ok(budgetService.getById(id, userId));
    }

    @PostMapping
    @Operation(summary = "Create a new budget")
    @ApiResponse(responseCode = "201", description = "Budget created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/budgets user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a budget")
    @ApiResponse(responseCode = "200", description = "Budget updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Budget or category not found")
    public ResponseEntity<BudgetResponse> update(@PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/budgets/{} user={}", id, userId);
        return ResponseEntity.ok(budgetService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget")
    @ApiResponse(responseCode = "204", description = "Budget deleted")
    @ApiResponse(responseCode = "404", description = "Budget not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/budgets/{} user={}", id, userId);
        budgetService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
