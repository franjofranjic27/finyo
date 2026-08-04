package ch.finyo.pillar3;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/pillar3/products")
@RequiredArgsConstructor
@Tag(name = "Pillar 3a Products", description = "Pillar 3a product catalog and comparison")
public class Pillar3ProductController {

    private final Pillar3ProductService productService;
    private final Pillar3CompareService compareService;
    private final Pillar3ProductPriceHistoryService priceHistoryService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List active pillar 3a products, optionally filtered by name, provider, ISIN or valor")
    @ApiResponse(responseCode = "200", description = "Active products returned successfully")
    public ResponseEntity<List<Pillar3ProductResponse>> getProducts(
            @RequestParam(required = false) String search
    ) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/pillar3/products user={} search={}", userId, search);
        return ResponseEntity.ok(productService.getActive(search));
    }

    @GetMapping("/{id}/price-history")
    @Operation(summary = "Daily closing prices for a pillar 3a product's fund",
            description = "Stored market prices for the product's ISIN, oldest first, for the last three "
                    + "years. Read-only; when nothing is stored yet a one-off backfill is triggered in the "
                    + "background — unlisted 3a funds legitimately stay empty. Inactive products resolve too, "
                    + "because saved scenarios keep referencing them.")
    @ApiResponse(responseCode = "200", description = "Price history returned")
    @ApiResponse(responseCode = "404", description = "Product not found")
    public ResponseEntity<Pillar3PriceHistoryResponse> getPriceHistory(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/pillar3/products/{}/price-history user={}", id, userId);
        return ResponseEntity.ok(priceHistoryService.getPriceHistory(id));
    }

    @PostMapping("/compare")
    @Operation(summary = "Compare projected final capital of up to 10 pillar 3a products over a savings horizon")
    @ApiResponse(responseCode = "200", description = "Comparison calculated; products sorted by final capital descending")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "One or more product IDs not found")
    public ResponseEntity<Pillar3CompareResponse> compare(@Valid @RequestBody Pillar3CompareRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/pillar3/products/compare user={} products={} years={}",
                userId, request.productIds().size(), request.years());
        return ResponseEntity.ok(compareService.compare(request));
    }
}
