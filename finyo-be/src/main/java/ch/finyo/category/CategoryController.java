package ch.finyo.category;

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
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Manage transaction categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all categories for the current user")
    @ApiResponse(responseCode = "200", description = "Categories returned successfully")
    public ResponseEntity<List<CategoryResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/categories user={}", userId);
        return ResponseEntity.ok(categoryService.getAll(userId));
    }

    @GetMapping("/top-level")
    @Operation(summary = "List top-level categories (no parent) for the current user")
    @ApiResponse(responseCode = "200", description = "Top-level categories returned successfully")
    public ResponseEntity<List<CategoryResponse>> getTopLevel() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/categories/top-level user={}", userId);
        return ResponseEntity.ok(categoryService.getTopLevel(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID")
    @ApiResponse(responseCode = "200", description = "Category found")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<CategoryResponse> getById(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/categories/{} user={}", id, userId);
        return ResponseEntity.ok(categoryService.getById(id, userId));
    }

    @PostMapping
    @Operation(summary = "Create a new category")
    @ApiResponse(responseCode = "201", description = "Category created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/categories user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    @ApiResponse(responseCode = "200", description = "Category updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/categories/{} user={}", id, userId);
        return ResponseEntity.ok(categoryService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category")
    @ApiResponse(responseCode = "204", description = "Category deleted")
    @ApiResponse(responseCode = "404", description = "Category not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/categories/{} user={}", id, userId);
        categoryService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
