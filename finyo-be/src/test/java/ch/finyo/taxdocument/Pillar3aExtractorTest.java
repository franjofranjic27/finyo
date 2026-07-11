package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pillar3aExtractorTest {

    private final Pillar3aExtractor extractor = new Pillar3aExtractor();

    @Test
    void supportsPillar3a() {
        assertThat(extractor.supports()).isEqualTo(TaxDocumentType.PILLAR_3A);
    }

    @Test
    void extractsContributionInstitutionAndYearFromForm21() {
        TaxDocumentExtractionResponse<Pillar3aFields> response =
                extractor.extract(TaxDocumentFixtures.load("pillar3a-form21.txt"));

        assertThat(response.type()).isEqualTo(TaxDocumentType.PILLAR_3A);
        // Year from the "q Jahr - Année - Anno" block, NOT from the 03.01.2026 issue date
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.fields().contribution()).isEqualByComparingTo("7056");
        assertThat(response.fields().institution()).isEqualTo("Muster Vorsorgestiftung");
    }

    @Test
    void mapsContributionToPillar3aContribution() {
        TaxDocumentExtractionResponse<Pillar3aFields> response =
                extractor.extract(TaxDocumentFixtures.load("pillar3a-form21.txt"));

        assertThat(response.taxYearMapping()).hasSize(1);
        ExtractedField mapping = response.taxYearMapping().getFirst();
        assertThat(mapping.fieldName()).isEqualTo("contribution");
        assertThat(mapping.value()).isEqualByComparingTo("7056");
        assertThat(mapping.targetTaxYearField()).isEqualTo("pillar3aContribution");
    }
}
