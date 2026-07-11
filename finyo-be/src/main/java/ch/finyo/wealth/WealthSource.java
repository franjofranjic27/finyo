package ch.finyo.wealth;

/** How the balance of a wealth bucket is resolved. */
public enum WealthSource {
    /** The user maintains the balance by hand (e.g. savings account, pillar 3a). */
    MANUAL,
    /** The balance is the live sum of portfolio positions in the linked asset classes. */
    PORTFOLIO
}
