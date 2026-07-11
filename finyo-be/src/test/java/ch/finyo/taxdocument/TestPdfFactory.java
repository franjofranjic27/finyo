package ch.finyo.taxdocument;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Renders the anonymized text fixtures from {@code src/test/resources/taxdocument}
 * into small, real PDFs in memory so integration tests exercise the full
 * PDFBox round trip (render → upload → PDFTextStripper → extractors).
 *
 * <p>Every fixture line becomes exactly one rendered text line — the
 * extractors' label matching and look-ahead logic depend on the line
 * structure surviving the round trip. Characters that Helvetica's WinAnsi
 * encoding cannot represent (e.g. the soft hyphens in the fixtures) are
 * replaced with a space instead of crashing the content stream; all
 * pattern-relevant labels and amounts use WinAnsi-safe characters.
 */
final class TestPdfFactory {

    private static final int LINES_PER_PAGE = 45;
    private static final float FONT_SIZE = 8f;
    private static final float LEADING = 12f;
    private static final float MARGIN_LEFT = 40f;
    private static final float TOP_BASELINE = 750f;

    private TestPdfFactory() {
    }

    static byte[] pdfFromFixture(String fixtureName) {
        return pdfFromText(TaxDocumentFixtures.load(fixtureName));
    }

    static byte[] pdfFromText(String text) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            List<String> lines = text.lines().map(line -> sanitizeForFont(font, line)).toList();
            for (int start = 0; start < lines.size(); start += LINES_PER_PAGE) {
                writePage(document, font, lines.subList(start, Math.min(start + LINES_PER_PAGE, lines.size())));
            }
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A syntactically valid PDF whose single page carries no text layer (i.e. a scan). */
    static byte[] blankPagePdf() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return save(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writePage(PDDocument document, PDFont font, List<String> lines) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(font, FONT_SIZE);
            content.setLeading(LEADING);
            content.newLineAtOffset(MARGIN_LEFT, TOP_BASELINE);
            for (String line : lines) {
                content.showText(line);
                content.newLine();
            }
            content.endText();
        }
    }

    private static String sanitizeForFont(PDFont font, String line) {
        StringBuilder sanitized = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            sanitized.append(isEncodable(font, c) ? c : ' ');
        }
        return sanitized.toString();
    }

    private static boolean isEncodable(PDFont font, char c) {
        try {
            font.encode(String.valueOf(c));
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }
}
