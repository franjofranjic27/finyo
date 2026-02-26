package ch.finyoapi.investment;

import jakarta.validation.constraints.Size;

public record InstrumentRequest(
        @Size(max = 20) String valor,
        @Size(max = 12) String isin,
        @Size(max = 20) String ticker,
        @Size(max = 200) String name,
        InstrumentType instrumentType,
        Integer sortOrder
) {}
