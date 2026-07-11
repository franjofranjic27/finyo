package ch.finyo.investment;

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
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Manage portfolio positions and track their performance")
public class PositionController {

    private final PositionService positionService;
    private final UserContextProvider userContextProvider;

    @PostMapping
    @Operation(summary = "Add a portfolio position",
            description = "Resolves the instrument by ISIN or valor (auto-creating it when unknown) and "
                    + "merges into an existing position of the same instrument using a weighted average purchase price.")
    @ApiResponse(responseCode = "201", description = "Position created or merged")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<PositionResponse> create(@Valid @RequestBody PositionRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/positions user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(positionService.create(request, userId));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk import portfolio positions",
            description = "Imports all valid rows and reports per-row errors; a failing row does not abort the import.")
    @ApiResponse(responseCode = "201", description = "Import finished, result contains per-row errors")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<BulkImportResultResponse> createBulk(@Valid @RequestBody PositionBulkRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/positions/bulk user={} rows={}", userId, request.positions().size());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(positionService.createBulk(request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a portfolio position")
    @ApiResponse(responseCode = "204", description = "Position deleted")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/positions/{} user={}", id, userId);
        positionService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
