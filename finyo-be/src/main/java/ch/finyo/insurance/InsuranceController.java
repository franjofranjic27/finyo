package ch.finyo.insurance;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/insurance")
@RequiredArgsConstructor
@Tag(name = "Insurance", description = "Swiss insurance overview and tracking")
public class InsuranceController {

    private final InsuranceService insuranceService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get insurance overview for the current user")
    @ApiResponse(responseCode = "200", description = "Insurance overview returned successfully")
    public ResponseEntity<List<InsuranceOverviewResponse>> getOverview() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/insurance user={}", userId);
        return ResponseEntity.ok(insuranceService.getOverview(userId));
    }

    @PatchMapping("/{insuranceTypeId}/status")
    @Operation(summary = "Update whether the current user has a given insurance type")
    @ApiResponse(responseCode = "200", description = "Insurance status updated successfully")
    @ApiResponse(responseCode = "404", description = "Insurance type not found")
    public ResponseEntity<InsuranceOverviewResponse> updateStatus(
        @PathVariable UUID insuranceTypeId,
        @RequestBody UpdateInsuranceStatusRequest request
    ) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/insurance/{}/status user={} hasInsurance={}", insuranceTypeId, userId, request.hasInsurance());
        return ResponseEntity.ok(insuranceService.updateStatus(insuranceTypeId, request.hasInsurance(), userId));
    }
}
