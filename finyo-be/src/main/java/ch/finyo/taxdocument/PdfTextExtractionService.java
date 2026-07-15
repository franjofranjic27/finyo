package ch.finyo.taxdocument;

import ch.finyo.common.DocumentBusyException;
import ch.finyo.common.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * PDFBox wrapper with the security caps from the plan: in-memory parsing
 * only, page-count and text-length limits, encrypted PDFs rejected and a
 * scan heuristic for PDFs without a text layer. The text cap is enforced
 * while stripping (not just between pages), so a single-page decompression
 * bomb cannot materialize unbounded text. Parsing itself is limited to a
 * small number of concurrent slots to bound CPU/memory pressure.
 *
 * <p>PII rule: extracted text is never logged — only page and char counts.
 */
@Slf4j
@Service
public class PdfTextExtractionService {

    private static final byte[] PDF_MAGIC_BYTES = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_PAGE_COUNT = 100;
    private static final int MAX_TEXT_LENGTH = 1_000_000;
    private static final int MIN_CHARS_PER_PAGE = 100;
    private static final int MIN_CHARS_TOTAL = 200;
    private static final int MAX_CONCURRENT_PARSES = 2;
    private static final Duration PARSE_SLOT_TIMEOUT = Duration.ofSeconds(5);

    private final int maxTextLength;
    private final Semaphore parseSlots;
    private final Duration parseSlotTimeout;

    public PdfTextExtractionService() {
        this(MAX_TEXT_LENGTH, MAX_CONCURRENT_PARSES, PARSE_SLOT_TIMEOUT);
    }

    /** Visible for tests: allows lowering the text cap and parse slots without huge fixtures. */
    PdfTextExtractionService(int maxTextLength, int maxConcurrentParses, Duration parseSlotTimeout) {
        this.maxTextLength = maxTextLength;
        this.parseSlots = new Semaphore(maxConcurrentParses, true);
        this.parseSlotTimeout = parseSlotTimeout;
    }

    public String extractText(byte[] pdfBytes) {
        validatePdfBytes(pdfBytes);
        acquireParseSlot();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new DocumentProcessingException("Encrypted PDF documents are not supported");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PAGE_COUNT) {
                throw new DocumentProcessingException(
                        "PDF exceeds the maximum of " + MAX_PAGE_COUNT + " pages");
            }
            String text = stripText(document, pageCount);
            if (text.strip().length() < Math.max(MIN_CHARS_TOTAL, MIN_CHARS_PER_PAGE * pageCount)) {
                throw new DocumentProcessingException(
                        "The PDF contains no extractable text and appears to be a scan. "
                                + "Scanned documents are not supported");
            }
            log.info("PDF text extracted: pages={} chars={}", pageCount, text.length());
            return text;
        } catch (InvalidPasswordException e) {
            throw new DocumentProcessingException("Encrypted PDF documents are not supported");
        } catch (TextLengthExceededException e) {
            throw new DocumentProcessingException("Document text exceeds the supported size");
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("PDF parsing failed: {}", e.getClass().getSimpleName());
            throw new DocumentProcessingException("The PDF document could not be parsed");
        } finally {
            parseSlots.release();
        }
    }

    /**
     * Bounds the number of concurrently running PDFBox parses so a burst of
     * uploads cannot exhaust CPU/heap. Honest 422 instead of queueing forever.
     */
    private void acquireParseSlot() {
        boolean acquired;
        try {
            acquired = parseSlots.tryAcquire(parseSlotTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw new DocumentBusyException(
                    "Server is busy processing other documents, please retry");
        }
    }

    /**
     * Strips page-wise with a stripper that enforces the text cap while writing,
     * so even a single malicious page cannot materialize unbounded text.
     */
    private String stripText(PDDocument document, int pageCount) throws IOException {
        BoundedTextStripper stripper = new BoundedTextStripper(maxTextLength);
        stripper.setSortByPosition(true);
        stripper.setSuppressDuplicateOverlappingText(true);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= pageCount && text.length() < maxTextLength; page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            text.append(stripper.getText(document));
        }
        return text.length() > maxTextLength ? text.substring(0, maxTextLength) : text.toString();
    }

    private void validatePdfBytes(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        if (pdfBytes.length < PDF_MAGIC_BYTES.length
                || !Arrays.equals(pdfBytes, 0, PDF_MAGIC_BYTES.length, PDF_MAGIC_BYTES, 0, PDF_MAGIC_BYTES.length)) {
            throw new IllegalArgumentException("File is not a PDF");
        }
    }

    /**
     * Aborts text stripping as soon as the cumulative extracted length exceeds
     * the cap — the check between pages alone would not stop a decompression
     * bomb hidden in a single page. The count survives across pages because the
     * same stripper instance is reused for the whole document.
     */
    private static final class BoundedTextStripper extends PDFTextStripper {

        private final int maxLength;
        private int writtenChars;

        BoundedTextStripper(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            writtenChars += text.length();
            if (writtenChars > maxLength) {
                throw new TextLengthExceededException();
            }
            super.writeString(text, textPositions);
        }
    }

    /** Marker to distinguish "text cap exceeded" from a genuinely corrupt PDF. */
    private static final class TextLengthExceededException extends IOException {
    }
}
