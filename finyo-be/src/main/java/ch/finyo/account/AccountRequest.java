package ch.finyo.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull AccountType type,
        @NotBlank @Size(max = 3) String currency,
        BigDecimal initialBalance,
        @Size(max = 7) String color,
        @Size(max = 34) String iban,
        @Size(max = 11) String bic,
        @Size(max = 50) String contractNumber,
        @Size(max = 100) String feeNote,
        AccountScope scope,
        Boolean toClose
) {}
