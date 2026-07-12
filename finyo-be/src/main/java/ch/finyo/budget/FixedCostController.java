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
@RequestMapping("/api/v1/fixed-costs")
@RequiredArgsConstructor
@Tag(name = "Fixed Costs", description = "Manage recurring fixed costs and subscriptions")
public class FixedCostController {

    private final FixedCostService fixedCostService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List fixed costs with per-year/per-month amounts and totals")
    @ApiResponse(responseCode = "200", description = "Fixed costs returned successfully")
    public ResponseEntity<FixedCostListResponse> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/fixed-costs user={}", userId);
        return ResponseEntity.ok(fixedCostService.getAll(userId));
    }

    @PostMapping
    @Operation(summary = "Create a new fixed cost")
    @ApiResponse(responseCode = "201", description = "Fixed cost created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<FixedCostResponse> create(@Valid @RequestBody FixedCostRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/fixed-costs user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(fixedCostService.create(request, userId));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk import fixed costs (upsert by normalized name)",
            description = "Each item is matched against the user's existing fixed costs by "
                    + "normalized name (trimmed, lowercased). A match updates that row — keeping "
                    + "the request's casing for the name — otherwise a new row is created. Items "
                    + "in the same payload that normalize to the same name resolve last-wins. "
                    + "A failing row is reported in the result without aborting the batch.")
    @ApiResponse(responseCode = "200", description = "Import processed; result contains per-row counts and errors")
    @ApiResponse(responseCode = "400", description = "Payload is empty, exceeds 500 items or contains invalid items")
    public ResponseEntity<FixedCostBulkResult> bulkImport(@Valid @RequestBody FixedCostBulkRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/fixed-costs/bulk user={} rows={}", userId, request.items().size());
        return ResponseEntity.ok(fixedCostService.bulkUpsert(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a fixed cost")
    @ApiResponse(responseCode = "200", description = "Fixed cost updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Fixed cost not found")
    public ResponseEntity<FixedCostResponse> update(@PathVariable UUID id, @Valid @RequestBody FixedCostRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/fixed-costs/{} user={}", id, userId);
        return ResponseEntity.ok(fixedCostService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a fixed cost")
    @ApiResponse(responseCode = "204", description = "Fixed cost deleted")
    @ApiResponse(responseCode = "404", description = "Fixed cost not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/fixed-costs/{} user={}", id, userId);
        fixedCostService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
