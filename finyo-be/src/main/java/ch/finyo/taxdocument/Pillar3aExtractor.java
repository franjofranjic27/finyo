package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Säule-3a-Bescheinigung (Form. 21 BVV 3). The institution is the first
 * non-label line after the trilingual section-a label block; the tax year
 * sits at the end of the "q Jahr - Année - Anno" block.
 */
@Component
public class Pillar3aExtractor implements TaxDocumentExtractor {

    private static final Pattern CONTRIBUTION = Pattern.compile("Total Beiträge an die Säule 3a");
    private static final Pattern SECTION_A_LABEL = Pattern.compile("^\\s*a\\s+Name und Sitz");
    private static final Pattern TRANSLATION_LINE = Pattern.compile("^\\s*(Nom et siège|Nome e sede)");
    private static final Pattern YEAR_BLOCK = Pattern.compile("Anno\\s+(20\\d{2})\\b");

    private static final int INSTITUTION_LOOK_AHEAD = 6;

    @Override
    public TaxDocumentType supports() {
        return TaxDocumentType.PILLAR_3A;
    }

    @Override
    public TaxDocumentExtractionResponse<Pillar3aFields> extract(String text) {
        List<String> lines = TextExtractionSupport.lines(text);

        BigDecimal contribution = TextExtractionSupport.findAmountAfterLabel(lines, CONTRIBUTION, 1);
        String institution = extractInstitution(lines);

        List<ExtractedField> taxYearMapping = new ArrayList<>();
        if (contribution != null) {
            taxYearMapping.add(new ExtractedField(
                    "contribution", "Total Beiträge Säule 3a", contribution, "pillar3aContribution"));
        }

        return new TaxDocumentExtractionResponse<>(
                TaxDocumentType.PILLAR_3A,
                extractTaxYear(text, lines),
                new Pillar3aFields(contribution, institution),
                List.copyOf(taxYearMapping));
    }

    private @Nullable String extractInstitution(List<String> lines) {
        int labelIndex = TextExtractionSupport.indexOfFirstMatch(lines, SECTION_A_LABEL, 0);
        if (labelIndex < 0) {
            return null;
        }
        for (int i = labelIndex + 1; i <= Math.min(labelIndex + INSTITUTION_LOOK_AHEAD, lines.size() - 1); i++) {
            String line = lines.get(i);
            if (line.isBlank() || TRANSLATION_LINE.matcher(line).find()) {
                continue;
            }
            return line.strip();
        }
        return null;
    }

    private @Nullable Integer extractTaxYear(String text, List<String> lines) {
        for (String line : lines) {
            Matcher matcher = YEAR_BLOCK.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return TextExtractionSupport.detectTaxYear(text);
    }
}
