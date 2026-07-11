package ch.finyo.investment;

/** Where the current price of a portfolio position comes from. */
public enum PriceSource {
    /** Live quote fetched from SIX during this request. */
    LIVE,
    /** Last persisted price on the instrument (SIX unavailable or manual override). */
    CACHE,
    /** No price known at all — falls back to the purchase price. */
    PURCHASE
}
