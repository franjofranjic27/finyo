package ch.finyo.taxdocument;

import ch.finyo.common.DocumentProcessingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractionServiceTest {

    private final PdfTextExtractionService service = new PdfTextExtractionService();

    @Test
    void extractsTextFromPdfWithTextLayer() throws IOException {
        byte[] pdf = pdfWithText(textLines());

        String text = service.extractText(pdf);

        assertThat(text).contains("Lohnausweis Zeile 01").contains("Lohnausweis Zeile 12");
    }

    @Test
    void rejectsBlankPagePdfAsScan() throws IOException {
        byte[] pdf = blankPdf(1);

        assertThatThrownBy(() -> service.extractText(pdf))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("scan");
    }

    @Test
    void rejectsRandomBytesAsNotAPdf() {
        byte[] notAPdf = "definitely not a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.extractText(notAPdf))
                .withMessageContaining("not a PDF");
    }

    @Test
    void rejectsEmptyBytes() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.extractText(new byte[0]))
                .withMessageContaining("empty");
    }

    @Test
    void wrapsCorruptPdfWithValidMagicBytesInDocumentProcessingException() {
        byte[] corrupt = "%PDF-1.7 this is not really a pdf body".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.extractText(corrupt))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void rejectsEncryptedPdf() throws IOException {
        byte[] pdf = encryptedPdf();

        assertThatThrownBy(() -> service.extractText(pdf))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("ncrypted");
    }

    @Test
    void rejectsPdfExceedingPageCap() throws IOException {
        byte[] pdf = blankPdf(101);

        assertThatThrownBy(() -> service.extractText(pdf))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("pages");
    }

    @Test
    void rejectsSinglePageWhoseTextExceedsTheCap() throws IOException {
        PdfTextExtractionService cappedService =
                new PdfTextExtractionService(100, 2, Duration.ofSeconds(5));
        byte[] pdf = pdfWithText(textLines()); // one page, well over 100 chars

        assertThatThrownBy(() -> cappedService.extractText(pdf))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("exceeds the supported size");
    }

    @Test
    void rejectsWhenNoParseSlotBecomesAvailable() throws IOException {
        PdfTextExtractionService busyService =
                new PdfTextExtractionService(1_000_000, 0, Duration.ofMillis(50));
        byte[] pdf = pdfWithText(textLines());

        assertThatThrownBy(() -> busyService.extractText(pdf))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("busy");
    }

    @Test
    void releasesParseSlotAfterFailedExtraction() throws IOException {
        PdfTextExtractionService singleSlotService =
                new PdfTextExtractionService(1_000_000, 1, Duration.ofMillis(200));
        byte[] scan = blankPdf(1);
        byte[] valid = pdfWithText(textLines());

        assertThatThrownBy(() -> singleSlotService.extractText(scan))
                .isInstanceOf(DocumentProcessingException.class);

        assertThat(singleSlotService.extractText(valid)).contains("Lohnausweis Zeile 01");
    }

    private static String[] textLines() {
        String[] lines = new String[12];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = "Lohnausweis Zeile %02d mit genuegend Text fuer die Scan-Heuristik".formatted(i + 1);
        }
        return lines;
    }

    private static byte[] pdfWithText(String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(14f);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            return save(document);
        }
    }

    private static byte[] blankPdf(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            return save(document);
        }
    }

    private static byte[] encryptedPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return save(document);
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
