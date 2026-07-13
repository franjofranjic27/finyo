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
@RequestMapping("/api/v1/category-rules")
@RequiredArgsConstructor
@Tag(name = "Category Rules", description = "Keyword rules that auto-categorize imported transactions")
public class CategoryRuleController {

    private final CategoryRuleService categoryRuleService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List all category rules for the current user")
    @ApiResponse(responseCode = "200", description = "Category rules returned successfully")
    public ResponseEntity<List<CategoryRuleResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/category-rules user={}", userId);
        return ResponseEntity.ok(categoryRuleService.getAll(userId));
    }

    @PostMapping
    @Operation(summary = "Create a new category rule")
    @ApiResponse(responseCode = "201", description = "Category rule created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Category not found")
    @ApiResponse(responseCode = "409", description = "A rule with this keyword already exists")
    public ResponseEntity<CategoryRuleResponse> create(@Valid @RequestBody CategoryRuleRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/category-rules user={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRuleService.create(request, userId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category rule")
    @ApiResponse(responseCode = "200", description = "Category rule updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Category rule or category not found")
    @ApiResponse(responseCode = "409", description = "A rule with this keyword already exists")
    public ResponseEntity<CategoryRuleResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody CategoryRuleRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/category-rules/{} user={}", id, userId);
        return ResponseEntity.ok(categoryRuleService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category rule")
    @ApiResponse(responseCode = "204", description = "Category rule deleted")
    @ApiResponse(responseCode = "404", description = "Category rule not found")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/category-rules/{} user={}", id, userId);
        categoryRuleService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }
}
