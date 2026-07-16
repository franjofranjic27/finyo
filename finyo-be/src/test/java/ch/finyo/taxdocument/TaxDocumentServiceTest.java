package ch.finyo.taxdocument;

import ch.finyo.common.DocumentProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Uses the real classifier and extractors against the text fixtures; only the
 * PDF parsing step is mocked. Pins down the guard semantics: a confidently
 * detected wrong type is rejected with the detected type attached, while an
 * UNKNOWN classification still gets extracted (benefit of the doubt).
 */
@ExtendWith(MockitoExtension.class)
class TaxDocumentServiceTest {

    @Mock
    private PdfTextExtractionService pdfTextExtractionService;

    private TaxDocumentService service;

    private final MockMultipartFile file =
            new MockMultipartFile("file", "document.pdf", "application/pdf", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        service = new TaxDocumentService(
                pdfTextExtractionService,
                new DocumentClassifier(),
                List.of(new SalaryCertificateExtractor(), new Pillar3aExtractor()));
    }

    @Test
    void classifyReturnsDetectedTypeAndConfidence() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn(TaxDocumentFixtures.load("salary-certificate.txt"));

        ClassificationResponse response = service.classify(file);

        assertThat(response.detectedType()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(response.confidence()).isGreaterThanOrEqualTo(0.25);
    }

    @Test
    void extractDispatchesToTheMatchingExtractor() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn(TaxDocumentFixtures.load("salary-certificate.txt"));

        TaxDocumentExtractionResponse<?> response = service.extract(file, TaxDocumentType.SALARY_CERTIFICATE);

        assertThat(response.type()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields()).isInstanceOf(SalaryCertificateFields.class);
    }

    @Test
    void extractRejectsConfidentlyDetectedWrongTypeWithDetectedTypeAttached() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn(TaxDocumentFixtures.load("pillar3a-form21.txt"));

        assertThatThrownBy(() -> service.extract(file, TaxDocumentType.SALARY_CERTIFICATE))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("Säule-3a-Bescheinigung")
                .hasMessageContaining("Lohnausweis")
                .extracting(ex -> ((DocumentProcessingException) ex).getDetectedType())
                .isEqualTo(TaxDocumentType.PILLAR_3A.name());
    }

    @Test
    void extractProceedsWhenClassificationIsUnknown() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn("Lorem ipsum dolor sit amet, no tax document keywords here");

        TaxDocumentExtractionResponse<?> response = service.extract(file, TaxDocumentType.SALARY_CERTIFICATE);

        assertThat(response.type()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(response.taxYearMapping()).isEmpty();
    }

    @Test
    void extractRejectsTypesWithoutExtractor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.extract(file, TaxDocumentType.UNKNOWN))
                .withMessageContaining("No extractor");
    }

    @Test
    void rejectsEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.classify(empty))
                .withMessageContaining("empty");
    }

    @Test
    void analyzeClassifiesAndExtractsInOnePass() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn(TaxDocumentFixtures.load("salary-certificate.txt"));

        DocumentAnalysis analysis = service.analyze(new byte[]{1, 2, 3});

        assertThat(analysis.classification().detectedType()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(analysis.classification().confidence()).isGreaterThanOrEqualTo(0.25);
        assertThat(analysis.extraction()).isNotNull();
        assertThat(analysis.extraction().taxYear()).isEqualTo(2025);
        verify(pdfTextExtractionService, times(1)).extractText(any(byte[].class));
    }

    /** UNKNOWN is a normal outcome for batch ingestion — the document goes to review, not to an error. */
    @Test
    void analyzeReturnsUnknownWithoutExtractionInsteadOfThrowing() {
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn("Lorem ipsum dolor sit amet, no tax document keywords here");

        DocumentAnalysis analysis = service.analyze(new byte[]{1, 2, 3});

        assertThat(analysis.classification().detectedType()).isEqualTo(TaxDocumentType.UNKNOWN);
        assertThat(analysis.extraction()).isNull();
    }

    /**
     * A type nobody registered an extractor for must not blow up the batch either:
     * here the classifier detects PILLAR_3A but only the salary extractor is wired.
     */
    @Test
    void analyzeReturnsNoExtractionWhenNoExtractorIsRegisteredForTheDetectedType() {
        TaxDocumentService salaryOnly = new TaxDocumentService(
                pdfTextExtractionService, new DocumentClassifier(), List.of(new SalaryCertificateExtractor()));
        given(pdfTextExtractionService.extractText(any(byte[].class)))
                .willReturn(TaxDocumentFixtures.load("pillar3a-form21.txt"));

        DocumentAnalysis analysis = salaryOnly.analyze(new byte[]{1, 2, 3});

        assertThat(analysis.classification().detectedType()).isEqualTo(TaxDocumentType.PILLAR_3A);
        assertThat(analysis.extraction()).isNull();
    }
}
