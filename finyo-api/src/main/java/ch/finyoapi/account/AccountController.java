package ch.finyoapi.account;

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
