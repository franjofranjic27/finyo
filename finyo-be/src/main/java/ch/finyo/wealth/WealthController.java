package ch.finyo.wealth;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/wealth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Wealth", description = "Net-worth overview across manual and portfolio-backed wealth buckets")
public class WealthController {

    private final WealthOverviewService overviewService;
    private final WealthBucketService bucketService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the net-worth overview",
            description = "Resolves all bucket balances (PORTFOLIO buckets live from the investment portfolio), "
                    + "computes shares and year-end forecasts and upserts today's net-worth snapshot.")
    @ApiResponse(responseCode = "200", description = "Overview returned successfully")
    public ResponseEntity<WealthOverviewResponse> getOverview() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/wealth user={}", userId);
        return ResponseEntity.ok(overviewService.getOverview(userId));
    }

    @GetMapping("/history")
    @Operation(summary = "Get the net-worth history",
            description = "Returns daily net-worth snapshots for the given number of months back.")
    @ApiResponse(responseCode = "200", description = "History returned successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<NetWorthHistoryResponse> getHistory(
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int months) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/wealth/history months={} user={}", months, userId);
        return ResponseEntity.ok(overviewService.getHistory(userId, months));
    }

    @PostMapping("/buckets")
    @Operation(summary = "Create a wealth bucket")
    @ApiResponse(responseCode = "201", description = "Wealth bucket created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<WealthBucketResponse> create(@Valid @RequestBody WealthBucketRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/wealth/buckets user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(bucketService.create(request, userId));
    }

    @PutMapping("/buckets/{id}")
    @Operation(summary = "Update a wealth bucket")
    @ApiResponse(responseCode = "200", description = "Wealth bucket updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Wealth bucket not found")
    public ResponseEntity<WealthBucketResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody WealthBucketRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/wealth/buckets/{} user={}", id, userId);
        return ResponseEntity.ok(bucketService.update(id, request, userId));
    }

    @DeleteMapping("/buckets/{id}")
    @Operation(summary = "Delete a wealth bucket")
    @ApiResponse(responseCode = "204", description = "Wealth bucket deleted")
    @ApiResponse(responseCode = "404", description = "Wealth bucket not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/wealth/buckets/{} user={}", id, userId);
        bucketService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
