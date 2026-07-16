package ch.finyo.investment;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/positions/{positionId}")
@RequiredArgsConstructor
@Tag(name = "Portfolio", description = "Manage portfolio positions and track their performance")
public class PositionDetailController {

    private final PositionDetailService positionDetailService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the detail view of a position",
            description = "Returns instrument master data, the priced valuation (same price fallback chain "
                    + "as the portfolio) and the position's share of the total portfolio value.")
    @ApiResponse(responseCode = "200", description = "Position detail returned successfully")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<PositionDetailResponse> getDetail(@PathVariable UUID positionId) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/positions/{} user={}", positionId, userId);
        return ResponseEntity.ok(positionDetailService.getDetail(positionId, userId));
    }

    @GetMapping("/price-history")
    @Operation(summary = "Daily closing prices for the position's instrument",
            description = "Stored market prices for the position's instrument, oldest first, for the "
                    + "last three years. Read-only; empty when the instrument is unlisted or has no ISIN.")
    @ApiResponse(responseCode = "200", description = "Price history returned")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<PriceHistoryResponse> getPriceHistory(@PathVariable UUID positionId) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/positions/{}/price-history user={}", positionId, userId);
        return ResponseEntity.ok(positionDetailService.getPriceHistory(positionId, userId));
    }

    @PatchMapping
    @Operation(summary = "Update a holding",
            description = "Partial update of the position itself: quantity, purchasePrice and currentPrice "
                    + "are applied only when present; purchaseDate is always applied (null = clear). "
                    + "An empty body is rejected.")
    @ApiResponse(responseCode = "200", description = "Position updated, fresh position detail returned")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<PositionDetailResponse> updatePosition(@PathVariable UUID positionId,
                                                                 @Valid @RequestBody PositionPatchRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/positions/{} user={}", positionId, userId);
        return ResponseEntity.ok(positionDetailService.updatePosition(positionId, request, userId));
    }

    @PatchMapping("/instrument")
    @Operation(summary = "Update the instrument behind a position",
            description = "Partial update: only the non-null fields of the request body are applied.")
    @ApiResponse(responseCode = "200", description = "Instrument updated, fresh position detail returned")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<PositionDetailResponse> updateInstrument(@PathVariable UUID positionId,
                                                                   @Valid @RequestBody InstrumentPatchRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PATCH /api/v1/positions/{}/instrument user={}", positionId, userId);
        return ResponseEntity.ok(positionDetailService.updateInstrument(positionId, request, userId));
    }

    @PostMapping(value = "/factsheet", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a factsheet PDF for a position",
            description = "Stores the PDF (max 10 MB) on the position's instrument, replacing any existing factsheet.")
    @ApiResponse(responseCode = "200", description = "Factsheet stored, fresh position detail returned")
    @ApiResponse(responseCode = "400", description = "File is empty, too large or not a PDF")
    @ApiResponse(responseCode = "404", description = "Position not found")
    public ResponseEntity<PositionDetailResponse> uploadFactsheet(@PathVariable UUID positionId,
                                                                  @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        // never log file contents — only sizes and ids
        log.info("POST /api/v1/positions/{}/factsheet user={} size={}", positionId, userId, file.getSize());
        return ResponseEntity.ok(positionDetailService.uploadFactsheet(positionId, file, userId));
    }

    @GetMapping("/factsheet")
    @Operation(summary = "Download the factsheet PDF of a position")
    @ApiResponse(responseCode = "200", description = "Factsheet PDF returned")
    @ApiResponse(responseCode = "404", description = "Position or factsheet not found")
    public ResponseEntity<byte[]> getFactsheet(@PathVariable UUID positionId) {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/positions/{}/factsheet user={}", positionId, userId);
        FactsheetDownload download = positionDetailService.getFactsheet(positionId, userId);
        // nosniff pins the declared PDF type; the CSP sandbox neuters any
        // script embedded in a malicious PDF rendered inline by the browser.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(download.filename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(download.content());
    }

    @DeleteMapping("/factsheet")
    @Operation(summary = "Delete the factsheet PDF of a position")
    @ApiResponse(responseCode = "204", description = "Factsheet deleted")
    @ApiResponse(responseCode = "404", description = "Position or factsheet not found")
    public ResponseEntity<Void> deleteFactsheet(@PathVariable UUID positionId) {
        String userId = userContextProvider.getUserId();
        log.info("DELETE /api/v1/positions/{}/factsheet user={}", positionId, userId);
        positionDetailService.deleteFactsheet(positionId, userId);
        return ResponseEntity.noContent().build();
    }
}
