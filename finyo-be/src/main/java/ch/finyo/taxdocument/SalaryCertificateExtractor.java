package ch.finyo.taxdocument;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lohnausweis (eidg. Formular 11). Ziffer 10.1 places its value on a
 * following line, hence the look-ahead of two lines.
 */
@Component
public class SalaryCertificateExtractor implements TaxDocumentExtractor {

    private static final Pattern GROSS_SALARY = Pattern.compile("Bruttolohn total");
    private static final Pattern SOCIAL_SECURITY = Pattern.compile("Beiträge AHV");
    private static final Pattern PENSION = Pattern.compile("10\\.1\\s+Ordentliche Beiträge");
    private static final Pattern NET_SALARY = Pattern.compile("Nettolohn");

    @Override
    public TaxDocumentType supports() {
        return TaxDocumentType.SALARY_CERTIFICATE;
    }

    @Override
    public TaxDocumentExtractionResponse<SalaryCertificateFields> extract(String text) {
        List<String> lines = TextExtractionSupport.lines(text);

        BigDecimal grossSalary = TextExtractionSupport.findAmountAfterLabel(lines, GROSS_SALARY, 2);
        BigDecimal socialSecurityContributions = TextExtractionSupport.findAmountAfterLabel(lines, SOCIAL_SECURITY, 2);
        BigDecimal pensionContributions = TextExtractionSupport.findAmountAfterLabel(lines, PENSION, 2);
        BigDecimal netSalary = TextExtractionSupport.findAmountAfterLabel(lines, NET_SALARY, 2);

        List<ExtractedField> taxYearMapping = new ArrayList<>();
        if (grossSalary != null) {
            taxYearMapping.add(new ExtractedField(
                    "grossSalary", "Bruttolohn total (Ziffer 8)", grossSalary, "grossEmploymentIncome"));
        }

        return new TaxDocumentExtractionResponse<>(
                TaxDocumentType.SALARY_CERTIFICATE,
                TextExtractionSupport.detectTaxYear(text),
                new SalaryCertificateFields(grossSalary, socialSecurityContributions, pensionContributions, netSalary),
                List.copyOf(taxYearMapping));
    }
}
