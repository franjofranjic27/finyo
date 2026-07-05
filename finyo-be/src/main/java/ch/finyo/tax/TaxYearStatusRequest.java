package ch.finyo.tax;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record TaxYearStatusRequest(
        @NotNull TaxYearStatus status,
        LocalDate effectiveDate
) {}
