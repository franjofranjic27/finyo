package ch.finyoapi.investment;

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
@RequestMapping("/api/v1/instruments")
@RequiredArgsConstructor
@Tag(name = "Investments", description = "Track financial instruments and market data")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all tracked instruments")
    @ApiResponse(responseCode = "200", description = "Instruments returned successfully")
    public ResponseEntity<List<InstrumentResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/instruments user={}", userId);
        return ResponseEntity.ok(instrumentService.getAll(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get instrument by ID")
    @ApiResponse(responseCode = "200", description = "Instrument found")
    @ApiResponse(responseCode = "404", description = "Instrument not found")
    public ResponseEntity<InstrumentResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/instruments/{} user={}", id, userId);
        return ResponseEntity.ok(instrumentService.getById(id, userId));
    }

    @GetMapping("/{id}/market-data")
    @Operation(summary = "Get live market data for an instrument from SIX Swiss Exchange")
    @ApiResponse(responseCode = "200", description = "Market data returned")
    @ApiResponse(responseCode = "404", description = "Instrument not found")
    public ResponseEntity<MarketDataResponse> getMarketData(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/instruments/{}/market-data user={}", id, userId);
        return ResponseEntity.ok(instrumentService.getMarketData(id, userId));
    }

    @PostMapping
    @Operation(summary = "Add a new instrument to track")
    @ApiResponse(responseCode = "201", description = "Instrument created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<InstrumentResponse> create(@Valid @RequestBody InstrumentRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/instruments user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(instrumentService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an instrument")
    @ApiResponse(responseCode = "200", description = "Instrument updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Instrument not found")
    public ResponseEntity<InstrumentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody InstrumentRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/instruments/{} user={}", id, userId);
        return ResponseEntity.ok(instrumentService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an instrument")
    @ApiResponse(responseCode = "204", description = "Instrument deleted")
    @ApiResponse(responseCode = "404", description = "Instrument not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/instruments/{} user={}", id, userId);
        instrumentService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
