package ch.finyo.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        LocalDate date,
        String description,
        UUID categoryId,
        String categoryName,
        UUID accountId,
        String accountName,
        TransactionSource source,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAmount(),
                t.getCurrency(),
                t.getDate(),
                t.getDescription(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getAccount().getId(),
                t.getAccount().getName(),
                t.getSource(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
