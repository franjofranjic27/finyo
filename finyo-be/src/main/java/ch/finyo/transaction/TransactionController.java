package ch.finyo.transaction;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Manage financial transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final CsvImportService csvImportService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List transactions with optional date filter and pagination")
    @ApiResponse(responseCode = "200", description = "Transactions returned successfully")
    public ResponseEntity<TransactionPageResponse> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "date,desc") String sort
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/transactions user={} from={} to={} page={} size={}", userId, from, to, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        return ResponseEntity.ok(transactionService.getAll(userId, from, to, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transaction by ID")
    @ApiResponse(responseCode = "200", description = "Transaction found")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/transactions/{} user={}", id, userId);
        return ResponseEntity.ok(transactionService.getById(id, userId));
    }

    @PostMapping
    @Operation(summary = "Create a new transaction")
    @ApiResponse(responseCode = "201", description = "Transaction created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Account or Category not found")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/transactions user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a transaction")
    @ApiResponse(responseCode = "200", description = "Transaction updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Transaction, Account or Category not found")
    public ResponseEntity<TransactionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request
    ) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/transactions/{} user={}", id, userId);
        return ResponseEntity.ok(transactionService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a transaction")
    @ApiResponse(responseCode = "204", description = "Transaction deleted")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/transactions/{} user={}", id, userId);
        transactionService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import transactions from a CSV file")
    @ApiResponse(responseCode = "200", description = "Import completed")
    @ApiResponse(responseCode = "400", description = "Invalid file or parameters")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<ImportResultResponse> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accountId") UUID accountId,
            @RequestParam(value = "preset", defaultValue = "CUSTOM") String preset,
            @RequestParam(value = "dateColumn", defaultValue = "0") int dateColumn,
            @RequestParam(value = "amountColumn", defaultValue = "1") int amountColumn,
            @RequestParam(value = "descriptionColumn", defaultValue = "2") int descriptionColumn,
            @RequestParam(value = "dateFormat", defaultValue = "dd.MM.yyyy") String dateFormat,
            @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader,
            @RequestParam(value = "skipDuplicates", defaultValue = "true") boolean skipDuplicates
    ) throws IOException {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/transactions/import/csv user={} accountId={} preset={}", userId, accountId, preset);
        ImportRequest request = new ImportRequest(
                accountId, preset, dateColumn, amountColumn, descriptionColumn,
                -1, dateFormat, ".", hasHeader, skipDuplicates
        );
        return ResponseEntity.ok(csvImportService.importCsv(file, request, userId));
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import transactions from an Excel file (.xlsx)")
    @ApiResponse(responseCode = "200", description = "Import completed")
    @ApiResponse(responseCode = "400", description = "Invalid file or parameters")
    @ApiResponse(responseCode = "404", description = "Account not found")
    public ResponseEntity<ImportResultResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("accountId") UUID accountId,
            @RequestParam(value = "preset", defaultValue = "CUSTOM") String preset,
            @RequestParam(value = "dateColumn", defaultValue = "0") int dateColumn,
            @RequestParam(value = "amountColumn", defaultValue = "1") int amountColumn,
            @RequestParam(value = "descriptionColumn", defaultValue = "2") int descriptionColumn,
            @RequestParam(value = "dateFormat", defaultValue = "dd.MM.yyyy") String dateFormat,
            @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader,
            @RequestParam(value = "skipDuplicates", defaultValue = "true") boolean skipDuplicates
    ) throws IOException {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/transactions/import/excel user={} accountId={} preset={}", userId, accountId, preset);
        ImportRequest request = new ImportRequest(
                accountId, preset, dateColumn, amountColumn, descriptionColumn,
                -1, dateFormat, ".", hasHeader, skipDuplicates
        );
        return ResponseEntity.ok(csvImportService.importExcel(file, request, userId));
    }
}
