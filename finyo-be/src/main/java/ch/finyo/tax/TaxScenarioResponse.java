package ch.finyo.tax;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaxScenarioResponse(
        UUID id,
        int taxYear,
        String name,
        boolean isDefault,
        TaxYearInputs inputs,
        TaxResultResponse calculation,
        OffsetDateTime createdAt
) {}
