package ch.finyo.tax;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/tax")
@RequiredArgsConstructor
@Tag(name = "Tax", description = "Swiss income tax calculations")
public class TaxController {

    private final TaxCalculationService taxCalculationService;
    private final Pillar3CalculationService pillar3CalculationService;

    @PostMapping("/calculate")
    @Operation(summary = "Calculate Swiss income tax (federal + cantonal + communal)")
    @ApiResponse(responseCode = "200", description = "Tax calculation completed successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<TaxResultResponse> calculate(
            @Valid @RequestBody TaxInputRequest request) {
        log.info("POST /api/v1/tax/calculate year={} canton={} civilStatus={}",
                request.taxYear(), request.cantonCode(), request.civilStatus());
        return ResponseEntity.ok(taxCalculationService.calculate(request));
    }

    @GetMapping("/communes")
    @Operation(summary = "List communes for a canton with their tax multipliers")
    @ApiResponse(responseCode = "200", description = "Communes returned successfully")
    public ResponseEntity<List<TaxCommuneMultiplier>> getCommunes(
            @RequestParam(defaultValue = "SG") String canton,
            @RequestParam(defaultValue = "2025") int year) {
        log.info("GET /api/v1/tax/communes canton={} year={}", canton, year);
        return ResponseEntity.ok(taxCalculationService.getCommunes(year, canton));
    }

    @PostMapping("/pillar3/calculate")
    @Operation(summary = "Calculate 3rd pillar (3a) impact on taxes and retirement savings")
    @ApiResponse(responseCode = "200", description = "Pillar 3a calculation completed successfully")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<Pillar3ResultResponse> calculatePillar3(
            @Valid @RequestBody Pillar3InputRequest request) {
        log.info("POST /api/v1/tax/pillar3/calculate years={} return={}%",
                request.yearsToRetirement(), request.assumedAnnualReturnPercent());
        return ResponseEntity.ok(pillar3CalculationService.calculate(request));
    }
}
