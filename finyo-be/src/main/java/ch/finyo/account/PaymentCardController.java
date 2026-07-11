package ch.finyo.account;

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

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Payment Cards", description = "Manage payment-card master data (no card numbers stored)")
public class PaymentCardController {

    private final PaymentCardService paymentCardService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all payment cards for the current user")
    @ApiResponse(responseCode = "200", description = "Payment cards returned successfully")
    public ResponseEntity<List<PaymentCardResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/cards user={}", userId);
        return ResponseEntity.ok(paymentCardService.getAll(userId));
    }

    @PostMapping
    @Operation(summary = "Create a new payment card")
    @ApiResponse(responseCode = "201", description = "Payment card created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<PaymentCardResponse> create(@Valid @RequestBody PaymentCardRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/cards user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentCardService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a payment card")
    @ApiResponse(responseCode = "200", description = "Payment card updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Payment card not found")
    public ResponseEntity<PaymentCardResponse> update(@PathVariable UUID id,
            @Valid @RequestBody PaymentCardRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/cards/{} user={}", id, userId);
        return ResponseEntity.ok(paymentCardService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a payment card")
    @ApiResponse(responseCode = "204", description = "Payment card deleted")
    @ApiResponse(responseCode = "404", description = "Payment card not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/cards/{} user={}", id, userId);
        paymentCardService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
