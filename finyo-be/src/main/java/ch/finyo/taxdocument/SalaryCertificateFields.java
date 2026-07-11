package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/** Lohnausweis (eidg. Formular 11): Ziffern 8, 9, 10.1 und 11. */
public record SalaryCertificateFields(
        @Nullable BigDecimal grossSalary,
        @Nullable BigDecimal socialSecurityContributions,
        @Nullable BigDecimal pensionContributions,
        @Nullable BigDecimal netSalary
) {}
