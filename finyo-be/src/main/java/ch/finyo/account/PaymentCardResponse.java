package ch.finyo.account;

import java.util.UUID;

public record PaymentCardResponse(
        UUID id,
        String name,
        String provider,
        UUID accountId,
        String accountName,
        String currency,
        String feeNote,
        AccountScope scope
) {
    public static PaymentCardResponse from(PaymentCard card, String accountName) {
        return new PaymentCardResponse(
                card.getId(),
                card.getName(),
                card.getProvider(),
                card.getAccountId(),
                accountName,
                card.getCurrency(),
                card.getFeeNote(),
                card.getScope()
        );
    }
}
