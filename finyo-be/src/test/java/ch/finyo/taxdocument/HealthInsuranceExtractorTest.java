package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthInsuranceExtractorTest {

    private final HealthInsuranceExtractor extractor = new HealthInsuranceExtractor();

    @Test
    void supportsHealthInsurance() {
        assertThat(extractor.supports()).isEqualTo(TaxDocumentType.HEALTH_INSURANCE);
    }

    @Test
    void extractsPremiumsAndTreatmentCostsFromSanitasStatement() {
        TaxDocumentExtractionResponse<HealthInsuranceFields> response =
                extractor.extract(TaxDocumentFixtures.load("health-insurance-sanitas.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.HEALTH_INSURANCE);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields().basicPremium()).isEqualByComparingTo("3612.00");
        assertThat(response.fields().supplementaryPremium()).isEqualByComparingTo("888.00");
        assertThat(response.fields().totalPremium()).isEqualByComparingTo("4500.00");
        assertThat(response.fields().uncoveredTreatmentCosts()).isEqualByComparingTo("750.00");
    }

    @Test
    void mapsTotalPremiumToDeductionInsurancePremiums() {
        TaxDocumentExtractionResponse<HealthInsuranceFields> response =
                extractor.extract(TaxDocumentFixtures.load("health-insurance-sanitas.txt"));

        assertThat(response.taxYearMapping()).hasSize(1);
        ExtractedField mapping = response.taxYearMapping().getFirst();
        assertThat(mapping.fieldName()).isEqualTo("totalPremium");
        assertThat(mapping.value()).isEqualByComparingTo("4500.00");
        assertThat(mapping.targetTaxYearField()).isEqualTo("deductionInsurancePremiums");
    }
}
