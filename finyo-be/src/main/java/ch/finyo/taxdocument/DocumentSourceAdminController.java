package ch.finyo.taxdocument;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cloud folders are operator-configured, not user-configured: granting the app
 * access to a SharePoint site requires an Entra admin (Sites.Selected), so there
 * is nothing an end user could set up here on their own. Hence an admin endpoint
 * and no settings UI.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/document-sources")
@RequiredArgsConstructor
@Tag(name = "Document Sources (Admin)", description = "Cloud folders that finyo ingests tax documents from")
public class DocumentSourceAdminController {

    private final DocumentSourceRepository documentSourceRepository;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "List configured cloud folders")
    public ResponseEntity<List<DocumentSourceResponse>> getAll() {
        return ResponseEntity.ok(documentSourceRepository.findAll().stream()
                .map(DocumentSourceResponse::from)
                .toList());
    }

    @PostMapping
    @Operation(summary = "Register a cloud folder to ingest from")
    public ResponseEntity<DocumentSourceResponse> create(@Valid @RequestBody DocumentSourceRequest request) {
        String userId = request.userId() == null ? userContextProvider.getUserId() : request.userId();
        log.info("POST /api/v1/admin/document-sources drive={} folder={} for user={}",
                request.driveId(), request.rootFolderPath(), userId);

        DocumentSource saved = documentSourceRepository.save(DocumentSource.builder()
                .userId(userId)
                .driveId(request.driveId())
                .rootFolderPath(request.rootFolderPath())
                .enabled(request.enabled())
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentSourceResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a cloud folder and every document ingested from it")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        DocumentSource source = documentSourceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Document source", id));
        documentSourceRepository.delete(source);
        return ResponseEntity.noContent().build();
    }
}
