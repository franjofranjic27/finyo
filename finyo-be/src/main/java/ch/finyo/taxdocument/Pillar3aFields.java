package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Säule-3a-Bescheinigung (Form. 21 BVV 3): Totalbeitrag und Vorsorgeeinrichtung. */
public record Pillar3aFields(
        @Nullable BigDecimal contribution,
        @Nullable String institution
) {}
