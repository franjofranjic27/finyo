package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentExtractorTest {

    private final AssessmentExtractor extractor = new AssessmentExtractor();

    @Test
    void supportsAssessment() {
        assertThat(extractor.supports()).isEqualTo(TaxDocumentType.ASSESSMENT);
    }

    @Test
    void extractsTaxTypeFactorsAndAmountFromStGallenTaxBill() {
        TaxDocumentExtractionResponse<AssessmentFields> response =
                extractor.extract(TaxDocumentFixtures.load("assessment-sg.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.ASSESSMENT);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields().taxType()).isEqualTo("KGSt");
        assertThat(response.fields().taxableIncome()).isEqualByComparingTo("64300");
        assertThat(response.fields().taxableWealth()).isEqualByComparingTo("89000");
        assertThat(response.fields().taxAmount()).isEqualByComparingTo("6480.00");
    }

    @Test
    void mapsTaxAmountToAssessedAmount() {
        TaxDocumentExtractionResponse<AssessmentFields> response =
                extractor.extract(TaxDocumentFixtures.load("assessment-sg.txt"));

        assertThat(response.taxYearMapping()).hasSize(1);
        ExtractedField mapping = response.taxYearMapping().getFirst();
        assertThat(mapping.fieldName()).isEqualTo("taxAmount");
        assertThat(mapping.value()).isEqualByComparingTo("6480.00");
        assertThat(mapping.targetTaxYearField()).isEqualTo("assessedAmount");
    }

    @Test
    void extractsViaPrimaryAnchorsOfDefinitiveAssessment() {
        String text = """
                Veranlagungsverfügung
                Direkte Bundessteuer (DBSt) 2024
                steuerbares Einkommen                    58'200
                steuerbares Vermögen                     75'000
                Steuerbetrag                             1'234.55
                """;

        TaxDocumentExtractionResponse<AssessmentFields> response = extractor.extract(text);

        assertThat(response.taxYear()).isEqualTo(2024);
        assertThat(response.fields().taxType()).isEqualTo("DBSt");
        assertThat(response.fields().taxableIncome()).isEqualByComparingTo("58200");
        assertThat(response.fields().taxableWealth()).isEqualByComparingTo("75000");
        assertThat(response.fields().taxAmount()).isEqualByComparingTo("1234.55");
    }

    @Test
    void extractionIsBestEffortAndToleratesMissingAnchors() {
        TaxDocumentExtractionResponse<AssessmentFields> response =
                extractor.extract("Veranlagungsverfügung ohne verwertbare Zahlen");

        assertThat(response.fields().taxType()).isNull();
        assertThat(response.fields().taxableIncome()).isNull();
        assertThat(response.fields().taxAmount()).isNull();
        assertThat(response.taxYearMapping()).isEmpty();
    }
}
