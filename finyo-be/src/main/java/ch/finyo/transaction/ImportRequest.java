package ch.finyo.transaction;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ImportRequest(
        @NotNull UUID accountId,
        String preset,            // UBS, RAIFFEISEN, POSTFINANCE, ZKB, CUSTOM
        Integer dateColumn,
        Integer amountColumn,
        Integer descriptionColumn,
        Integer currencyColumn,
        String dateFormat,
        String decimalSeparator,
        boolean hasHeader,
        boolean skipDuplicates
) {}
