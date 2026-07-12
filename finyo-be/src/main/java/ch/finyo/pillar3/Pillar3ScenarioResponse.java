package ch.finyo.pillar3;

import ch.finyo.tax.Pillar3ResultResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Pillar3ScenarioResponse(
        UUID id,
        String name,
        boolean isDefault,
        Pillar3ScenarioInputs inputs,
        /** Null when the scenario has no linked product or the product was deleted. */
        Pillar3ProductResponse product,
        /** Product-derived net return when linked, otherwise the stored snapshot percent. */
        double effectiveReturnPercent,
        Pillar3ResultResponse calculation,
        OffsetDateTime createdAt
) {}
