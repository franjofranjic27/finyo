package ch.finyo.pillar3;

import java.math.BigDecimal;

/**
 * Thin read port for other modules (currently the wealth overview): the two
 * figures of the default pillar 3a scenario that the auto-mirrored wealth row
 * needs, without the projection recomputation the full scenario responses
 * perform.
 */
public record Pillar3DefaultScenarioSummary(
        BigDecimal currentBalance,
        BigDecimal annualContribution
) {}
