package ch.finyoapi.account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        String currency,
        BigDecimal initialBalance,
        String color,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getCurrency(),
                account.getInitialBalance(),
                account.getColor(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
