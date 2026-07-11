package ch.finyo.account;

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
        String iban,
        String bic,
        String contractNumber,
        String feeNote,
        AccountScope scope,
        boolean toClose,
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
                account.getIban(),
                account.getBic(),
                account.getContractNumber(),
                account.getFeeNote(),
                account.getScope(),
                account.isToClose(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
