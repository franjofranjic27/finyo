package ch.finyo.taxdocument;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/tax/documents")
@RequiredArgsConstructor
@Tag(name = "Tax Documents",
        description = "Classify uploaded tax PDFs and extract field values (preview only — files are never stored)")
public class TaxDocumentController {

    private final TaxDocumentService taxDocumentService;
    private final UserContextProvider userContextProvider;

    @PostMapping(value = "/classify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Classify a tax document PDF")
    @ApiResponse(responseCode = "200", description = "Classification result (UNKNOWN when uncertain)")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed (scan, encrypted, corrupt)")
    public ResponseEntity<ClassificationResponse> classify(@RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/classify user={}", userId);
        return ResponseEntity.ok(taxDocumentService.classify(file));
    }

    @PostMapping(value = "/salary-certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from a salary certificate (Lohnausweis)")
    @ApiResponse(responseCode = "200", description = "Extraction result with tax year field mapping")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed or is a different document type")
    public ResponseEntity<TaxDocumentExtractionResponse<?>> extractSalaryCertificate(
            @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/salary-certificate user={}", userId);
        return ResponseEntity.ok(taxDocumentService.extract(file, TaxDocumentType.SALARY_CERTIFICATE));
    }

    @PostMapping(value = "/health-insurance", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from a health insurance premium statement")
    @ApiResponse(responseCode = "200", description = "Extraction result with tax year field mapping")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed or is a different document type")
    public ResponseEntity<TaxDocumentExtractionResponse<?>> extractHealthInsurance(
            @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/health-insurance user={}", userId);
        return ResponseEntity.ok(taxDocumentService.extract(file, TaxDocumentType.HEALTH_INSURANCE));
    }

    @PostMapping(value = "/securities-statement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from a bank securities tax statement")
    @ApiResponse(responseCode = "200", description = "Extraction result with tax year field mapping")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed or is a different document type")
    public ResponseEntity<TaxDocumentExtractionResponse<?>> extractSecuritiesStatement(
            @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/securities-statement user={}", userId);
        return ResponseEntity.ok(taxDocumentService.extract(file, TaxDocumentType.SECURITIES_STATEMENT));
    }

    @PostMapping(value = "/pillar3a", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from a pillar 3a contribution certificate (Form. 21 BVV 3)")
    @ApiResponse(responseCode = "200", description = "Extraction result with tax year field mapping")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed or is a different document type")
    public ResponseEntity<TaxDocumentExtractionResponse<?>> extractPillar3a(
            @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/pillar3a user={}", userId);
        return ResponseEntity.ok(taxDocumentService.extract(file, TaxDocumentType.PILLAR_3A));
    }

    @PostMapping(value = "/assessment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Extract fields from a tax assessment (Veranlagung/Steuerrechnung, best-effort)")
    @ApiResponse(responseCode = "200", description = "Extraction result with tax year field mapping")
    @ApiResponse(responseCode = "400", description = "File is empty or not a PDF")
    @ApiResponse(responseCode = "422", description = "Document could not be processed or is a different document type")
    public ResponseEntity<TaxDocumentExtractionResponse<?>> extractAssessment(
            @RequestParam("file") MultipartFile file) {
        String userId = userContextProvider.getUserId();
        log.info("POST /api/v1/tax/documents/assessment user={}", userId);
        return ResponseEntity.ok(taxDocumentService.extract(file, TaxDocumentType.ASSESSMENT));
    }
}
