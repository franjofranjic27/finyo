package ch.finyoapi.savings;

import ch.finyoapi.common.UserContextProvider;
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
@RequestMapping("/api/v1/savings")
@RequiredArgsConstructor
@Tag(name = "Savings", description = "Manage savings goals")
public class SavingsGoalController {

    private final SavingsGoalService savingsGoalService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List savings goals; pass includeArchived=true to include archived goals")
    @ApiResponse(responseCode = "200", description = "Savings goals returned successfully")
    public ResponseEntity<List<SavingsGoalResponse>> getAll(
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/savings user={} includeArchived={}", userId, includeArchived);
        List<SavingsGoalResponse> goals = includeArchived
                ? savingsGoalService.getAllIncludingArchived(userId)
                : savingsGoalService.getAll(userId);
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get savings goal by ID")
    @ApiResponse(responseCode = "200", description = "Savings goal found")
    @ApiResponse(responseCode = "404", description = "Savings goal not found")
    public ResponseEntity<SavingsGoalResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/savings/{} user={}", id, userId);
        return ResponseEntity.ok(savingsGoalService.getById(id, userId));
    }

    @PostMapping
    @Operation(summary = "Create a new savings goal")
    @ApiResponse(responseCode = "201", description = "Savings goal created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<SavingsGoalResponse> create(@Valid @RequestBody SavingsGoalRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/savings user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(savingsGoalService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a savings goal")
    @ApiResponse(responseCode = "200", description = "Savings goal updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Savings goal or account not found")
    public ResponseEntity<SavingsGoalResponse> update(@PathVariable UUID id, @Valid @RequestBody SavingsGoalRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/savings/{} user={}", id, userId);
        return ResponseEntity.ok(savingsGoalService.update(id, request, userId));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive a savings goal")
    @ApiResponse(responseCode = "200", description = "Savings goal archived")
    @ApiResponse(responseCode = "404", description = "Savings goal not found")
    public ResponseEntity<SavingsGoalResponse> archive(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/savings/{}/archive user={}", id, userId);
        return ResponseEntity.ok(savingsGoalService.archive(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a savings goal")
    @ApiResponse(responseCode = "204", description = "Savings goal deleted")
    @ApiResponse(responseCode = "404", description = "Savings goal not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/savings/{} user={}", id, userId);
        savingsGoalService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
