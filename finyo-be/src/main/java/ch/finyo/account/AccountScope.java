package ch.finyo.account;

/**
 * Whether an account or payment card belongs to the user's private or
 * business sphere. Defaults to {@link #PRIVATE} everywhere.
 */
public enum AccountScope {
    PRIVATE,
    BUSINESS
}
