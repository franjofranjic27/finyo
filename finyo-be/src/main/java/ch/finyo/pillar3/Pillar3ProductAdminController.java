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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/pillar3/products")
@RequiredArgsConstructor
@Tag(name = "Admin — Pillar 3a Products", description = "Manage the pillar 3a product catalog (ROLE_admin required)")
@PreAuthorize("hasRole('admin')")
public class Pillar3ProductAdminController {

    private final Pillar3ProductService productService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all pillar 3a products, including inactive ones")
    @ApiResponse(responseCode = "200", description = "Products returned successfully")
    public ResponseEntity<List<Pillar3ProductResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/admin/pillar3/products user={}", userId);
        return ResponseEntity.ok(productService.getAll());
    }

    @PostMapping
    @Operation(summary = "Create a new pillar 3a product")
    @ApiResponse(responseCode = "201", description = "Product created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "A product with the same ISIN already exists")
    public ResponseEntity<Pillar3ProductResponse> create(@Valid @RequestBody Pillar3ProductRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/admin/pillar3/products user={} isin={}", userId, request.isin());
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a pillar 3a product; set active=false to deactivate it")
    @ApiResponse(responseCode = "200", description = "Product updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @ApiResponse(responseCode = "409", description = "Another product with the same ISIN already exists")
    public ResponseEntity<Pillar3ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody Pillar3ProductRequest request
    ) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/admin/pillar3/products/{} user={}", id, userId);
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a pillar 3a product permanently (prefer deactivation via PUT)")
    @ApiResponse(responseCode = "204", description = "Product deleted")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/admin/pillar3/products/{} user={}", id, userId);
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/import")
    @Operation(summary = "Bulk import pillar 3a products (upsert by ISIN)",
            description = "Each row is processed independently: a failing row is reported in the result "
                    + "without aborting the batch. Rows with an existing ISIN update that product; "
                    + "active/sortOrder are only overwritten when provided. Duplicate ISINs within the "
                    + "same payload resolve last-wins. Re-importing the same payload is idempotent "
                    + "(created=0, updated=n).")
    @ApiResponse(responseCode = "200", description = "Import processed; result contains per-row counts and errors")
    @ApiResponse(responseCode = "400", description = "Payload is empty or malformed")
    public ResponseEntity<Pillar3ProductImportResult> importProducts(
            @Valid @RequestBody Pillar3ProductImportRequest request
    ) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/admin/pillar3/products/import user={} rows={}", userId, request.products().size());
        return ResponseEntity.ok(productService.importProducts(request));
    }
}
