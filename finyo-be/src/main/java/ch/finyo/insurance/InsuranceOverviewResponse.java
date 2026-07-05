package ch.finyo.insurance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InsuranceOverviewResponse(
    UUID id,
    String code,
    String nameEn,
    String nameDe,
    String descriptionEn,
    String descriptionDe,
    boolean mandatory,
    boolean recommended,
    BigDecimal typicalCostMin,
    BigDecimal typicalCostMax,
    String comparisonUrl,
    int sortOrder,
    boolean hasInsurance,
    OffsetDateTime statusUpdatedAt
) {}
