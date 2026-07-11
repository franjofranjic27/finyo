package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Bank-Steuerverzeichnis/-auszug: Total Steuerwert, Bruttoertrag und Gebühren. */
public record SecuritiesStatementFields(
        @Nullable BigDecimal totalTaxValue,
        @Nullable BigDecimal totalGrossIncome,
        @Nullable BigDecimal totalFees
) {}
