package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bank securities statements in two known layouts:
 * <ul>
 *   <li><b>Summary table</b> (Raiffeisen "Steuerverzeichnis"): a "Total" row in
 *       the "Zusammenfassung" section carries Steuerwert, Erträge A/B and
 *       Verrechnungssteuer as columns; the gross income is the sum of the
 *       middle columns.</li>
 *   <li><b>Header block</b> (Yuh "Steuerauszug"): the amounts sit on a line
 *       below the "Total Steuerwert der …" label block — first token is the
 *       Steuerwert, last token the total gross income.</li>
 * </ul>
 */
@Component
public class SecuritiesStatementExtractor implements TaxDocumentExtractor {

    private static final Pattern SUMMARY_SECTION = Pattern.compile("Zusammenfassung");
    /** A "Total" row directly followed by an amount (excludes "Total Gebühren" etc.). */
    private static final Pattern SUMMARY_TOTAL_ROW = Pattern.compile("^\\s*Total\\s+[\\d'\\u2019]");
    private static final Pattern TOTAL_FEES = Pattern.compile("Total Gebühren");
    private static final Pattern TAX_VALUE_HEADER = Pattern.compile("Total Steuerwert der");
    private static final Pattern FEES_HEADER = Pattern.compile("^\\s*Spesen\\b");

    private static final int HEADER_BLOCK_LOOK_AHEAD = 6;

    @Override
    public TaxDocumentType supports() {
        return TaxDocumentType.SECURITIES_STATEMENT;
    }

    @Override
    public TaxDocumentExtractionResponse<SecuritiesStatementFields> extract(String text) {
        List<String> lines = TextExtractionSupport.lines(text);

        SecuritiesStatementFields fields = text.contains("Steuerverzeichnis")
                ? extractSummaryTableLayout(lines)
                : extractHeaderBlockLayout(lines);

        List<ExtractedField> taxYearMapping = new ArrayList<>();
        if (fields.totalTaxValue() != null) {
            taxYearMapping.add(new ExtractedField(
                    "totalTaxValue", "Total Steuerwert", fields.totalTaxValue(), "netWealth"));
        }
        if (fields.totalGrossIncome() != null) {
            taxYearMapping.add(new ExtractedField(
                    "totalGrossIncome", "Total Bruttoertrag", fields.totalGrossIncome(), "investmentIncome"));
        }

        return new TaxDocumentExtractionResponse<>(
                TaxDocumentType.SECURITIES_STATEMENT,
                TextExtractionSupport.detectTaxYear(text),
                fields,
                List.copyOf(taxYearMapping));
    }

    private SecuritiesStatementFields extractSummaryTableLayout(List<String> lines) {
        BigDecimal totalTaxValue = null;
        BigDecimal totalGrossIncome = null;
        int summaryIndex = TextExtractionSupport.indexOfFirstMatch(lines, SUMMARY_SECTION, 0);
        int totalRowIndex = summaryIndex < 0
                ? -1
                : TextExtractionSupport.indexOfFirstMatch(lines, SUMMARY_TOTAL_ROW, summaryIndex + 1);
        if (totalRowIndex >= 0) {
            List<BigDecimal> tokens = TextExtractionSupport.amountTokens(lines.get(totalRowIndex), 0);
            totalTaxValue = tokens.isEmpty() ? null : tokens.getFirst();
            totalGrossIncome = sumGrossIncomeColumns(tokens);
        }
        BigDecimal totalFees = TextExtractionSupport.findAmountAfterLabel(lines, TOTAL_FEES, 1);
        return new SecuritiesStatementFields(totalTaxValue, totalGrossIncome, totalFees);
    }

    /**
     * Total row columns: Steuerwert, Ertrag Rubrik A, Ertrag Rubrik B,
     * Verrechnungssteuer — the gross income is everything between the first
     * and the last column.
     */
    private @Nullable BigDecimal sumGrossIncomeColumns(List<BigDecimal> tokens) {
        if (tokens.size() < 3) {
            return tokens.size() == 2 ? tokens.get(1) : null;
        }
        return tokens.subList(1, tokens.size() - 1).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SecuritiesStatementFields extractHeaderBlockLayout(List<String> lines) {
        BigDecimal totalTaxValue = null;
        BigDecimal totalGrossIncome = null;
        int headerIndex = TextExtractionSupport.indexOfFirstMatch(lines, TAX_VALUE_HEADER, 0);
        if (headerIndex >= 0) {
            for (int i = headerIndex + 1; i <= Math.min(headerIndex + HEADER_BLOCK_LOOK_AHEAD, lines.size() - 1); i++) {
                List<BigDecimal> tokens = TextExtractionSupport.amountTokens(lines.get(i), 0);
                if (tokens.isEmpty()) {
                    continue;
                }
                totalTaxValue = tokens.getFirst();
                totalGrossIncome = tokens.getLast();
                break;
            }
        }
        BigDecimal totalFees = TextExtractionSupport.findAmountAfterLabel(lines, FEES_HEADER, 2);
        return new SecuritiesStatementFields(totalTaxValue, totalGrossIncome, totalFees);
    }
}
