package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Krankenkassen-Steuerbescheinigung: KVG/VVG-Prämien und Behandlungskosten. */
public record HealthInsuranceFields(
        @Nullable BigDecimal basicPremium,
        @Nullable BigDecimal supplementaryPremium,
        @Nullable BigDecimal totalPremium,
        @Nullable BigDecimal uncoveredTreatmentCosts
) {}
