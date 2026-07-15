package ch.finyo.taxdocument;

import ch.finyo.common.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates validate → extract text → classify → guard → extract fields.
 * Uploaded files are processed in memory only and never persisted.
 */
@Slf4j
@Service
public class TaxDocumentService {

    private final PdfTextExtractionService pdfTextExtractionService;
    private final DocumentClassifier documentClassifier;
    private final Map<TaxDocumentType, TaxDocumentExtractor> extractorsByType;

    public TaxDocumentService(PdfTextExtractionService pdfTextExtractionService,
                              DocumentClassifier documentClassifier,
                              List<TaxDocumentExtractor> extractors) {
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.documentClassifier = documentClassifier;
        this.extractorsByType = extractors.stream()
                .collect(Collectors.toUnmodifiableMap(TaxDocumentExtractor::supports, Function.identity()));
    }

    public ClassificationResponse classify(MultipartFile file) {
        String text = extractText(file);
        ClassificationResponse classification = documentClassifier.classify(text);
        log.info("Document classified: type={} confidence={}",
                classification.detectedType(), classification.confidence());
        return classification;
    }

    /**
     * Classification acts as a guard for the typed endpoints: a confidently
     * detected mismatch is rejected with 422; an UNKNOWN classification gets
     * the benefit of the doubt and extraction is attempted anyway.
     */
    public TaxDocumentExtractionResponse<?> extract(MultipartFile file, TaxDocumentType expectedType) {
        TaxDocumentExtractor extractor = extractorsByType.get(expectedType);
        if (extractor == null) {
            throw new IllegalArgumentException("No extractor available for document type " + expectedType);
        }
        String text = extractText(file);
        ClassificationResponse classification = documentClassifier.classify(text);
        TaxDocumentType detectedType = classification.detectedType();
        if (detectedType != expectedType && detectedType != TaxDocumentType.UNKNOWN) {
            throw new DocumentProcessingException(
                    "Dokument wurde als '%s' erkannt, nicht als '%s'"
                            .formatted(detectedType.getDisplayLabel(), expectedType.getDisplayLabel()),
                    detectedType.name());
        }
        log.info("Document extraction: expectedType={} detectedType={} confidence={}",
                expectedType, detectedType, classification.confidence());
        return extractor.extract(text);
    }

    /**
     * Single-parse analysis for batch ingestion: classify, then extract with the
     * matching extractor. A type that no extractor supports is not an error here
     * — the document simply carries no extraction and ends up in the review inbox.
     *
     * <p>The PDF is parsed exactly once; callers must not classify and extract
     * separately, since each parse takes one of the scarce parse slots.
     */
    public DocumentAnalysis analyze(byte[] pdfBytes) {
        String text = pdfTextExtractionService.extractText(pdfBytes);
        ClassificationResponse classification = documentClassifier.classify(text);
        TaxDocumentExtractor extractor = extractorsByType.get(classification.detectedType());
        log.info("Document analyzed: type={} confidence={} extractable={}",
                classification.detectedType(), classification.confidence(), extractor != null);
        return new DocumentAnalysis(classification, extractor == null ? null : extractor.extract(text));
    }

    private String extractText(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        try {
            return pdfTextExtractionService.extractText(file.getBytes());
        } catch (IOException e) {
            throw new DocumentProcessingException("Failed to read uploaded file");
        }
    }
}
