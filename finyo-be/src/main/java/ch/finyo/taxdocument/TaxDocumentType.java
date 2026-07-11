package ch.finyo.taxdocument;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaxDocumentType {
    SALARY_CERTIFICATE("Lohnausweis"),
    HEALTH_INSURANCE("Krankenkassen-Steuerbescheinigung"),
    SECURITIES_STATEMENT("Steuerverzeichnis"),
    PILLAR_3A("Säule-3a-Bescheinigung"),
    ASSESSMENT("Veranlagung"),
    UNKNOWN("Unbekannt");

    private final String displayLabel;
}
