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
@RequestMapping("/api/v1/tax/years")
@RequiredArgsConstructor
@Tag(name = "Tax Years", description = "Manage persisted tax years with payments and deadlines")
public class TaxYearController {

    private final TaxYearService taxYearService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all tax years for the current user, newest first")
    @ApiResponse(responseCode = "200", description = "Tax years returned successfully")
    public ResponseEntity<List<TaxYearSummaryResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/tax/years user={}", userId);
        return ResponseEntity.ok(taxYearService.getAll(userId));
    }

    @GetMapping("/{year}")
    @Operation(summary = "Get a tax year with calculation, payments and deadlines")
    @ApiResponse(responseCode = "200", description = "Tax year found")
    @ApiResponse(responseCode = "404", description = "Tax year not found")
    public ResponseEntity<TaxYearDetailResponse> getByYear(@PathVariable @Min(2020) @Max(2030) int year) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/tax/years/{} user={}", year, userId);
        return ResponseEntity.ok(taxYearService.getByYear(year, userId));
    }

    @PutMapping("/{year}")
    @Operation(summary = "Create or update the inputs of a tax year (upsert)")
    @ApiResponse(responseCode = "200", description = "Tax year saved")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<TaxYearDetailResponse> upsert(@PathVariable @Min(2020) @Max(2030) int year,
                                                        @Valid @RequestBody TaxYearRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/tax/years/{} user={}", year, userId);
        return ResponseEntity.ok(taxYearService.upsert(year, request, userId));
    }

    @PatchMapping("/{year}/status")
    @Operation(summary = "Change the status of a tax year")
    @ApiResponse(responseCode = "200", description = "Status updated")
    @ApiResponse(responseCode = "404", description = "Tax year not found")
    @ApiResponse(responseCode = "409", description = "Illegal status transition")
    public ResponseEntity<TaxYearDetailResponse> updateStatus(@PathVariable @Min(2020) @Max(2030) int year,
                                                              @Valid @RequestBody TaxYearStatusRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/tax/years/{}/status user={}", year, userId);
        return ResponseEntity.ok(taxYearService.updateStatus(year, request, userId));
    }

    @DeleteMapping("/{year}")
    @Operation(summary = "Delete a tax year including its payments and deadlines")
    @ApiResponse(responseCode = "204", description = "Tax year deleted")
    @ApiResponse(responseCode = "404", description = "Tax year not found")
    public ResponseEntity<Void> delete(@PathVariable @Min(2020) @Max(2030) int year) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/tax/years/{} user={}", year, userId);
        taxYearService.delete(year, userId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Payments
    // -------------------------------------------------------------------------

    @PostMapping("/{year}/payments")
    @Operation(summary = "Record a payment for a tax year")
    @ApiResponse(responseCode = "201", description = "Payment created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Tax year not found")
    public ResponseEntity<TaxPaymentResponse> addPayment(@PathVariable @Min(2020) @Max(2030) int year,
                                                         @Valid @RequestBody TaxPaymentRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/years/{}/payments user={}", year, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(taxYearService.addPayment(year, request, userId));
    }

    @PutMapping("/{year}/payments/{id}")
    @Operation(summary = "Update a payment of a tax year")
    @ApiResponse(responseCode = "200", description = "Payment updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Tax year or payment not found")
    public ResponseEntity<TaxPaymentResponse> updatePayment(@PathVariable @Min(2020) @Max(2030) int year,
                                                            @PathVariable UUID id,
                                                            @Valid @RequestBody TaxPaymentRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/tax/years/{}/payments/{} user={}", year, id, userId);
        return ResponseEntity.ok(taxYearService.updatePayment(year, id, request, userId));
    }

    @DeleteMapping("/{year}/payments/{id}")
    @Operation(summary = "Delete a payment of a tax year")
    @ApiResponse(responseCode = "204", description = "Payment deleted")
    @ApiResponse(responseCode = "404", description = "Tax year or payment not found")
    public ResponseEntity<Void> deletePayment(@PathVariable @Min(2020) @Max(2030) int year, @PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/tax/years/{}/payments/{} user={}", year, id, userId);
        taxYearService.deletePayment(year, id, userId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Deadlines
    // -------------------------------------------------------------------------

    @PostMapping("/{year}/deadlines")
    @Operation(summary = "Add a deadline to a tax year")
    @ApiResponse(responseCode = "201", description = "Deadline created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Tax year not found")
    public ResponseEntity<TaxDeadlineResponse> addDeadline(@PathVariable @Min(2020) @Max(2030) int year,
                                                           @Valid @RequestBody TaxDeadlineRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/years/{}/deadlines user={}", year, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(taxYearService.addDeadline(year, request, userId));
    }

    @PutMapping("/{year}/deadlines/{id}")
    @Operation(summary = "Update a deadline of a tax year (including the done toggle)")
    @ApiResponse(responseCode = "200", description = "Deadline updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Tax year or deadline not found")
    public ResponseEntity<TaxDeadlineResponse> updateDeadline(@PathVariable @Min(2020) @Max(2030) int year,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody TaxDeadlineRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/tax/years/{}/deadlines/{} user={}", year, id, userId);
        return ResponseEntity.ok(taxYearService.updateDeadline(year, id, request, userId));
    }

    @DeleteMapping("/{year}/deadlines/{id}")
    @Operation(summary = "Delete a deadline of a tax year")
    @ApiResponse(responseCode = "204", description = "Deadline deleted")
    @ApiResponse(responseCode = "404", description = "Tax year or deadline not found")
    public ResponseEntity<Void> deleteDeadline(@PathVariable @Min(2020) @Max(2030) int year, @PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/tax/years/{}/deadlines/{} user={}", year, id, userId);
        taxYearService.deleteDeadline(year, id, userId);
        return ResponseEntity.noContent().build();
    }
}
