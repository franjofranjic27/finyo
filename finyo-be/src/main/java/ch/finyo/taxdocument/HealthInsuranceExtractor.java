package ch.finyo.taxdocument;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Krankenkassen-Steuerbescheinigung (Prämien- und Kostenübersicht). */
@Component
public class HealthInsuranceExtractor implements TaxDocumentExtractor {

    private static final Pattern BASIC_PREMIUM = Pattern.compile("\\bKVG\\b");
    private static final Pattern SUPPLEMENTARY_PREMIUM = Pattern.compile("\\bVVG\\b");
    /** First line starting with "Total" — the premium total; the treatment-cost total has a leading label. */
    private static final Pattern TOTAL_PREMIUM = Pattern.compile("^\\s*Total\\b");
    private static final Pattern UNCOVERED_COSTS = Pattern.compile("Nicht versicherte Behandlungskosten");

    @Override
    public TaxDocumentType supports() {
        return TaxDocumentType.HEALTH_INSURANCE;
    }

    @Override
    public TaxDocumentExtractionResponse<HealthInsuranceFields> extract(String text) {
        List<String> lines = TextExtractionSupport.lines(text);

        BigDecimal basicPremium = TextExtractionSupport.findAmountAfterLabel(lines, BASIC_PREMIUM, 1);
        BigDecimal supplementaryPremium = TextExtractionSupport.findAmountAfterLabel(lines, SUPPLEMENTARY_PREMIUM, 1);
        BigDecimal totalPremium = TextExtractionSupport.findAmountAfterLabel(lines, TOTAL_PREMIUM, 1);
        BigDecimal uncoveredTreatmentCosts = TextExtractionSupport.findAmountAfterLabel(lines, UNCOVERED_COSTS, 1);

        List<ExtractedField> taxYearMapping = new ArrayList<>();
        if (totalPremium != null) {
            taxYearMapping.add(new ExtractedField(
                    "totalPremium", "Total Prämien", totalPremium, "deductionInsurancePremiums"));
        }

        return new TaxDocumentExtractionResponse<>(
                TaxDocumentType.HEALTH_INSURANCE,
                TextExtractionSupport.detectTaxYear(text),
                new HealthInsuranceFields(basicPremium, supplementaryPremium, totalPremium, uncoveredTreatmentCosts),
                List.copyOf(taxYearMapping));
    }
}
