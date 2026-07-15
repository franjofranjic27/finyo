package ch.finyo.taxdocument;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.tax.FieldApplyResult;
import ch.finyo.tax.TaxYearService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The review inbox: what the user sees, confirms or discards. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentInboxService {

    private final DocumentRepository documentRepository;
    private final TaxYearService taxYearService;

    @Transactional(readOnly = true)
    public List<DocumentResponse> getAll(String userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    /**
     * Writes the confirmed fields into the tax year. Unlike the automated path this
     * overwrites existing values — the user has seen them side by side and decided.
     *
     * @param fieldNames the fields ticked in the UI; empty means all of them
     */
    @Transactional
    public DocumentResponse apply(UUID documentId, int year, Set<String> fieldNames, String userId) {
        Document document = load(documentId, userId);
        List<ExtractedField> fields = document.getExtractedFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Document has no extracted fields to apply");
        }

        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (ExtractedField field : fields) {
            boolean selected = fieldNames.isEmpty() || fieldNames.contains(field.fieldName());
            if (selected && field.targetTaxYearField() != null && field.value() != null) {
                values.put(field.targetTaxYearField(), field.value());
            }
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("None of the selected fields map to a tax year field");
        }

        FieldApplyResult result = taxYearService.applyExtractedFields(userId, year, values, true);
        log.info("User applied document {} to tax year {}: {}", documentId, year, result.applied());

        return DocumentResponse.from(documentRepository.save(document.toBuilder()
                .status(DocumentStatus.APPLIED)
                .build()));
    }

    @Transactional
    public DocumentResponse dismiss(UUID documentId, String userId) {
        Document document = load(documentId, userId);
        return DocumentResponse.from(documentRepository.save(document.toBuilder()
                .status(DocumentStatus.DISMISSED)
                .build()));
    }

    /** Ownership check: another user's document is a 404, as everywhere else. */
    private Document load(UUID documentId, String userId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));
    }
}
