package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecuritiesStatementExtractorTest {

    private final SecuritiesStatementExtractor extractor = new SecuritiesStatementExtractor();

    @Test
    void supportsSecuritiesStatement() {
        assertThat(extractor.supports()).isEqualTo(TaxDocumentType.SECURITIES_STATEMENT);
    }

    @Test
    void extractsSummaryTotalsFromRaiffeisenLayout() {
        TaxDocumentExtractionResponse<SecuritiesStatementFields> response =
                extractor.extract(TaxDocumentFixtures.load("securities-raiffeisen.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.SECURITIES_STATEMENT);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields().totalTaxValue()).isEqualByComparingTo("89666.50");
        // Sum of Ertrag Rubrik A (8.75) + Rubrik B (421.30) from the FIRST
        // Total row of the Zusammenfassung — not the detail Total row later on
        assertThat(response.fields().totalGrossIncome()).isEqualByComparingTo("430.05");
        assertThat(response.fields().totalFees()).isEqualByComparingTo("180.20");
    }

    @Test
    void extractsHeaderBlockTotalsFromYuhLayoutWithCommaGrouping() {
        TaxDocumentExtractionResponse<SecuritiesStatementFields> response =
                extractor.extract(TaxDocumentFixtures.load("securities-yuh.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.SECURITIES_STATEMENT);
        assertThat(response.taxYear()).isEqualTo(2025);
        // Amounts sit below the "Total Steuerwert der A, B, DA-1 und USA-Werte" header block
        assertThat(response.fields().totalTaxValue()).isEqualByComparingTo("24750");
        assertThat(response.fields().totalGrossIncome()).isEqualByComparingTo("85");
        assertThat(response.fields().totalFees()).isEqualByComparingTo("25");
    }

    @Test
    void mapsTaxValueAndGrossIncomeToTaxYearFields() {
        TaxDocumentExtractionResponse<SecuritiesStatementFields> response =
                extractor.extract(TaxDocumentFixtures.load("securities-raiffeisen.txt"));

        assertThat(response.taxYearMapping()).hasSize(2);
        assertThat(response.taxYearMapping())
                .extracting(ExtractedField::fieldName, ExtractedField::targetTaxYearField)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("totalTaxValue", "netWealth"),
                        org.assertj.core.groups.Tuple.tuple("totalGrossIncome", "investmentIncome"));
    }
}
