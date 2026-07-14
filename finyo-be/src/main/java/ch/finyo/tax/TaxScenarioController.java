package ch.finyo.tax;

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

import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/tax/years/{year}/scenarios")
@RequiredArgsConstructor
@Tag(name = "Tax Scenarios", description = "Manage saved tax scenarios per tax year")
public class TaxScenarioController {

    private final TaxScenarioService taxScenarioService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all scenarios of a tax year, newest first")
    @ApiResponse(responseCode = "200", description = "Scenarios returned successfully")
    public ResponseEntity<List<TaxScenarioResponse>> list(@PathVariable @Min(2020) @Max(2030) int year) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/tax/years/{}/scenarios user={}", year, userId);
        return ResponseEntity.ok(taxScenarioService.list(year, userId));
    }

    @PostMapping
    @Operation(summary = "Save a new scenario snapshot for a tax year")
    @ApiResponse(responseCode = "201", description = "Scenario created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Tax year already has a default scenario")
    public ResponseEntity<TaxScenarioResponse> create(@PathVariable @Min(2020) @Max(2030) int year,
                                                      @Valid @RequestBody TaxScenarioRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/years/{}/scenarios user={}", year, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(taxScenarioService.create(year, request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name and inputs of a scenario",
            description = "isDefault in the request is ignored; use PATCH /{id}/default to change the default.")
    @ApiResponse(responseCode = "200", description = "Scenario updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Tax year or scenario not found")
    public ResponseEntity<TaxScenarioResponse> update(@PathVariable @Min(2020) @Max(2030) int year,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody TaxScenarioRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/tax/years/{}/scenarios/{} user={}", year, id, userId);
        return ResponseEntity.ok(taxScenarioService.update(year, id, request, userId));
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Mark a scenario as the default of its tax year")
    @ApiResponse(responseCode = "200", description = "Default scenario updated")
    @ApiResponse(responseCode = "404", description = "Tax year or scenario not found")
    public ResponseEntity<TaxScenarioResponse> setDefault(@PathVariable @Min(2020) @Max(2030) int year,
                                                          @PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/tax/years/{}/scenarios/{}/default user={}", year, id, userId);
        return ResponseEntity.ok(taxScenarioService.setDefault(year, id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a scenario of a tax year")
    @ApiResponse(responseCode = "204", description = "Scenario deleted")
    @ApiResponse(responseCode = "404", description = "Tax year or scenario not found")
    public ResponseEntity<Void> delete(@PathVariable @Min(2020) @Max(2030) int year, @PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/tax/years/{}/scenarios/{} user={}", year, id, userId);
        taxScenarioService.delete(year, id, userId);
        return ResponseEntity.noContent().build();
    }
}
