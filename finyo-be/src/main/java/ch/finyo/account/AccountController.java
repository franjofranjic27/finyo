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
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Manage financial accounts")
public class AccountController {

    private final AccountService accountService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all accounts for the current user")
    @ApiResponse(responseCode = "200", description = "Accounts returned successfully")
    public ResponseEntity<List<AccountResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/accounts user={}", userId);
        return ResponseEntity.ok(accountService.getAll(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<AccountResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/accounts/{} user={}", id, userId);
        return ResponseEntity.ok(accountService.getById(id, userId));
    }

    @PostMapping
    @Operation(summary = "Create a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/accounts user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request, userId));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk import accounts (upsert by IBAN, else by normalized name)",
            description = "Each item is matched against the user's existing accounts by normalized "
                    + "IBAN when the item carries one, otherwise by normalized name (trimmed, "
                    + "lowercased). A match updates that account — an IBAN match may rename it — "
                    + "otherwise a new account is created. Balances are optional: a missing initial "
                    + "balance defaults to zero on create and keeps the stored value on update. "
                    + "Items in the same payload that resolve to the same key update last-wins. "
                    + "A failing row (e.g. invalid IBAN checksum) is reported in the result without "
                    + "aborting the batch.")
    @ApiResponse(responseCode = "200", description = "Import processed; result contains per-row counts and errors")
    @ApiResponse(responseCode = "400", description = "Payload is empty, exceeds 200 items or contains invalid items")
    public ResponseEntity<AccountBulkResult> bulkImport(@Valid @RequestBody AccountBulkRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/accounts/bulk user={} rows={}", userId, request.items().size());
        return ResponseEntity.ok(accountService.bulkUpsert(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an account")
    @ApiResponse(responseCode = "200", description = "Account updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<AccountResponse> update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/accounts/{} user={}", id, userId);
        return ResponseEntity.ok(accountService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an account")
    @ApiResponse(responseCode = "204", description = "Account deleted")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/accounts/{} user={}", id, userId);
        accountService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
