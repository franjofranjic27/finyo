package ch.finyo.transaction;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionRequest(
        @NotNull BigDecimal amount,
        String currency,
        @NotNull LocalDate date,
        String description,
        UUID categoryId,
        @NotNull UUID accountId
) {}
