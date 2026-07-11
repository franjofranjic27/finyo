package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Veranlagung/Steuerrechnung der Gemeinde — best-effort: cantonal layouts
 * vary (provisorische Steuerrechnung vs. definitive Veranlagungsverfügung),
 * so every field falls back to alternative anchors and may stay {@code null}.
 */
@Component
public class AssessmentExtractor implements TaxDocumentExtractor {

    private static final Pattern TAX_AMOUNT = Pattern.compile("Total mutmasslicher Steuerbetrag");
    private static final Pattern TAX_AMOUNT_FALLBACK = Pattern.compile("^\\s*Steuerbetrag\\b");
    private static final Pattern TAXABLE_INCOME = Pattern.compile("steuerbares Einkommen");
    private static final Pattern TAXABLE_INCOME_FALLBACK = Pattern.compile("^\\s*Einkommen\\b");
    private static final Pattern TAXABLE_WEALTH = Pattern.compile("steuerbares Vermögen");
    private static final Pattern TAXABLE_WEALTH_FALLBACK = Pattern.compile("^\\s*Vermögen\\b");
    private static final Pattern TAX_YEAR = Pattern.compile("\\((?:KGSt|DBSt)\\)\\s*(20\\d{2})");

    @Override
    public TaxDocumentType supports() {
        return TaxDocumentType.ASSESSMENT;
    }

    @Override
    public TaxDocumentExtractionResponse<AssessmentFields> extract(String text) {
        List<String> lines = TextExtractionSupport.lines(text);

        String taxType = detectTaxType(text);
        BigDecimal taxableIncome = findAmount(lines, TAXABLE_INCOME, TAXABLE_INCOME_FALLBACK);
        BigDecimal taxableWealth = findAmount(lines, TAXABLE_WEALTH, TAXABLE_WEALTH_FALLBACK);
        BigDecimal taxAmount = findAmount(lines, TAX_AMOUNT, TAX_AMOUNT_FALLBACK);

        List<ExtractedField> taxYearMapping = new ArrayList<>();
        if (taxAmount != null) {
            taxYearMapping.add(new ExtractedField(
                    "taxAmount", "Total Steuerbetrag", taxAmount, "assessedAmount"));
        }

        return new TaxDocumentExtractionResponse<>(
                TaxDocumentType.ASSESSMENT,
                extractTaxYear(text),
                new AssessmentFields(taxType, taxableIncome, taxableWealth, taxAmount),
                List.copyOf(taxYearMapping));
    }

    private @Nullable String detectTaxType(String text) {
        if (text.contains("KGSt") || text.contains("Kantons- und Gemeindesteuer")) {
            return "KGSt";
        }
        if (text.contains("DBSt") || text.contains("Direkte Bundessteuer")) {
            return "DBSt";
        }
        return null;
    }

    private @Nullable BigDecimal findAmount(List<String> lines, Pattern primary, Pattern fallback) {
        BigDecimal value = TextExtractionSupport.findAmountAfterLabel(lines, primary, 1);
        return value != null ? value : TextExtractionSupport.findAmountAfterLabel(lines, fallback, 1);
    }

    private @Nullable Integer extractTaxYear(String text) {
        Matcher matcher = TAX_YEAR.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return TextExtractionSupport.detectTaxYear(text);
    }
}
