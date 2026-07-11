package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentClassifierTest {

    private final DocumentClassifier classifier = new DocumentClassifier();

    @ParameterizedTest
    @CsvSource({
            "salary-certificate.txt,      SALARY_CERTIFICATE",
            "health-insurance-sanitas.txt, HEALTH_INSURANCE",
            "securities-raiffeisen.txt,   SECURITIES_STATEMENT",
            "securities-yuh.txt,          SECURITIES_STATEMENT",
            "pillar3a-form21.txt,         PILLAR_3A",
            "assessment-sg.txt,           ASSESSMENT",
    })
    void classifiesEachFixtureToItsOwnType(String fixture, TaxDocumentType expectedType) {
        ClassificationResponse result = classifier.classify(TaxDocumentFixtures.load(fixture));

        assertThat(result.detectedType()).isEqualTo(expectedType);
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.25).isLessThanOrEqualTo(1.0);
    }

    @Test
    void classifiesEmptyTextAsUnknown() {
        ClassificationResponse result = classifier.classify("");

        assertThat(result.detectedType()).isEqualTo(TaxDocumentType.UNKNOWN);
        assertThat(result.confidence()).isZero();
    }

    @Test
    void classifiesUnrelatedTextAsUnknown() {
        ClassificationResponse result = classifier.classify(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
                        + "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");

        assertThat(result.detectedType()).isEqualTo(TaxDocumentType.UNKNOWN);
        assertThat(result.confidence()).isLessThan(0.25);
    }
}
