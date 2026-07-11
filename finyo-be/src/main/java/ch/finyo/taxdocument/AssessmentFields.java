package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Veranlagung/Steuerrechnung (best-effort): Steuerart, Faktoren und Steuerbetrag. */
public record AssessmentFields(
        @Nullable String taxType,
        @Nullable BigDecimal taxableIncome,
        @Nullable BigDecimal taxableWealth,
        @Nullable BigDecimal taxAmount
) {}
