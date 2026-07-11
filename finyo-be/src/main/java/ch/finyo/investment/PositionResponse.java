package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PositionResponse(
        UUID id,
        UUID instrumentId,
        String name,
        String isin,
        String valor,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        OffsetDateTime createdAt
) {
    public static PositionResponse from(Position position, Instrument instrument) {
        return new PositionResponse(
                position.getId(),
                position.getInstrumentId(),
                instrument.getName(),
                instrument.getIsin(),
                instrument.getValor(),
                position.getQuantity(),
                position.getPurchasePrice(),
                position.getCreatedAt()
        );
    }
}
