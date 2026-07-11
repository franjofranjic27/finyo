package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryCertificateExtractorTest {

    private final SalaryCertificateExtractor extractor = new SalaryCertificateExtractor();

    @Test
    void supportsSalaryCertificate() {
        assertThat(extractor.supports()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
    }

    @Test
    void extractsAllFieldsFromFederalForm11() {
        TaxDocumentExtractionResponse<SalaryCertificateFields> response =
                extractor.extract(TaxDocumentFixtures.load("salary-certificate.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields().grossSalary()).isEqualByComparingTo("84000");
        assertThat(response.fields().socialSecurityContributions()).isEqualByComparingTo("5334");
        // Ziffer 10.1: the value sits on a following line, not on the label line
        assertThat(response.fields().pensionContributions()).isEqualByComparingTo("4200");
        assertThat(response.fields().netSalary()).isEqualByComparingTo("74466");
    }

    @Test
    void mapsGrossSalaryToGrossEmploymentIncome() {
        TaxDocumentExtractionResponse<SalaryCertificateFields> response =
                extractor.extract(TaxDocumentFixtures.load("salary-certificate.txt"));

        assertThat(response.taxYearMapping()).hasSize(1);
        ExtractedField mapping = response.taxYearMapping().getFirst();
        assertThat(mapping.fieldName()).isEqualTo("grossSalary");
        assertThat(mapping.value()).isEqualByComparingTo("84000");
        assertThat(mapping.targetTaxYearField()).isEqualTo("grossEmploymentIncome");
        assertThat(mapping.label()).isNotBlank();
    }

    @Test
    void returnsNullFieldsAndEmptyMappingForUnrelatedText() {
        TaxDocumentExtractionResponse<SalaryCertificateFields> response =
                extractor.extract("Lorem ipsum dolor sit amet");

        assertThat(response.taxYear()).isNull();
        assertThat(response.fields().grossSalary()).isNull();
        assertThat(response.fields().netSalary()).isNull();
        assertThat(response.taxYearMapping()).isEmpty();
    }
}
