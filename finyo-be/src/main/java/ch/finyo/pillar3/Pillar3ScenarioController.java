package ch.finyo.pillar3;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/pillar3/scenarios")
@RequiredArgsConstructor
@Tag(name = "Pillar 3a Scenarios", description = "Manage saved pillar 3a projection scenarios")
public class Pillar3ScenarioController {

    private final Pillar3ScenarioService pillar3ScenarioService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all pillar 3a scenarios of the caller, newest first")
    @ApiResponse(responseCode = "200", description = "Scenarios returned successfully")
    public ResponseEntity<List<Pillar3ScenarioResponse>> list() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/pillar3/scenarios user={}", userId);
        return ResponseEntity.ok(pillar3ScenarioService.list(userId));
    }

    @PostMapping
    @Operation(summary = "Save a new pillar 3a scenario snapshot")
    @ApiResponse(responseCode = "201", description = "Scenario created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Linked product not found")
    @ApiResponse(responseCode = "409", description = "User already has a default scenario")
    public ResponseEntity<Pillar3ScenarioResponse> create(@Valid @RequestBody Pillar3ScenarioRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/pillar3/scenarios user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pillar3ScenarioService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name and inputs of a scenario",
            description = "isDefault in the request is ignored; use PATCH /{id}/default to change the default.")
    @ApiResponse(responseCode = "200", description = "Scenario updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Scenario or linked product not found")
    public ResponseEntity<Pillar3ScenarioResponse> update(@PathVariable UUID id,
                                                          @Valid @RequestBody Pillar3ScenarioRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/pillar3/scenarios/{} user={}", id, userId);
        return ResponseEntity.ok(pillar3ScenarioService.update(id, request, userId));
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Mark a scenario as the caller's default")
    @ApiResponse(responseCode = "200", description = "Default scenario updated")
    @ApiResponse(responseCode = "404", description = "Scenario not found")
    public ResponseEntity<Pillar3ScenarioResponse> setDefault(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/pillar3/scenarios/{}/default user={}", id, userId);
        return ResponseEntity.ok(pillar3ScenarioService.setDefault(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a scenario")
    @ApiResponse(responseCode = "204", description = "Scenario deleted")
    @ApiResponse(responseCode = "404", description = "Scenario not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/pillar3/scenarios/{} user={}", id, userId);
        pillar3ScenarioService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
