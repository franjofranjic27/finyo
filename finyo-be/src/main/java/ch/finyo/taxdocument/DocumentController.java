package ch.finyo.taxdocument;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Review inbox for tax documents ingested from a cloud folder")
public class DocumentController {

    private final DocumentInboxService documentInboxService;
    private final DocumentSyncService documentSyncService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List ingested documents")
    public ResponseEntity<List<DocumentResponse>> getAll() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/documents user={}", userId);
        return ResponseEntity.ok(documentInboxService.getAll(userId));
    }

    /**
     * Manual trigger. Also the seam that makes the whole pipeline integration-testable
     * without a scheduler, and gives the UI a "sync now" button — a 15-minute
     * schedule alone would leave the inbox stale with nothing the user can do.
     */
    @PostMapping("/sync")
    @Operation(summary = "Pull new documents from the configured cloud folders now")
    @ApiResponse(responseCode = "200", description = "Sync finished; counts per outcome")
    @ApiResponse(responseCode = "500", description = "No cloud folder configured")
    public ResponseEntity<DocumentSyncService.SyncResult> sync() {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/documents/sync user={}", userId);
        return ResponseEntity.ok(documentSyncService.syncForUser(userId));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply the extracted values of a document to a tax year")
    @ApiResponse(responseCode = "200", description = "Values written; document marked as applied")
    @ApiResponse(responseCode = "400", description = "Document has no applicable fields")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<DocumentResponse> apply(@PathVariable UUID id,
                                                  @Valid @RequestBody DocumentApplyRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/documents/{}/apply year={} user={}", id, request.year(), userId);
        return ResponseEntity.ok(
                documentInboxService.apply(id, request.year(), request.fieldNames(), userId));
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "Discard a document without importing it")
    @ApiResponse(responseCode = "404", description = "Document not found")
    public ResponseEntity<DocumentResponse> dismiss(@PathVariable UUID id) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/documents/{}/dismiss user={}", id, userId);
        return ResponseEntity.ok(documentInboxService.dismiss(id, userId));
    }
}
