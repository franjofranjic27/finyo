package ch.finyo.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PaymentCardRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String provider,
        UUID accountId,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 100) String feeNote,
        AccountScope scope
) {}
