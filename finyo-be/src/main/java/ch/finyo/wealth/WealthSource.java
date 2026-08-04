package ch.finyo.wealth;

/**
 * How the balance of a wealth row is resolved. Only MANUAL rows are persisted;
 * PORTFOLIO and PILLAR3 mark the auto-mirrored rows the overview synthesizes.
 */
public enum WealthSource {
    /** The user maintains the balance by hand (e.g. savings account). */
    MANUAL,
    /** Auto row: the live CHF total of the investment portfolio. */
    PORTFOLIO,
    /** Auto row: the current balance of the user's default pillar 3a scenario. */
    PILLAR3
}
