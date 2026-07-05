package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InstrumentResponse(
        UUID id,
        String valor,
        String isin,
        String ticker,
        String name,
        InstrumentType instrumentType,
        int sortOrder,
        BigDecimal lastPrice,
        OffsetDateTime lastPriceUpdatedAt,
        OffsetDateTime createdAt
) {
    public static InstrumentResponse from(Instrument i) {
        return new InstrumentResponse(
                i.getId(), i.getValor(), i.getIsin(), i.getTicker(), i.getName(),
                i.getInstrumentType(), i.getSortOrder(), i.getLastPrice(),
                i.getLastPriceUpdatedAt(), i.getCreatedAt()
        );
    }
}
