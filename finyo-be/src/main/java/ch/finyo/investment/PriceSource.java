package ch.finyo.investment;

/**
 * Where the price of a portfolio position comes from — and therefore how much it is worth.
 *
 * The predecessor had LIVE / CACHE / PURCHASE, and the last of those was the bug: whenever SIX
 * was unreachable — which, with the API key empty by default, was always — every position
 * quietly fell back to its own purchase price. The portfolio then reported a gain of exactly
 * 0.00 and looked entirely plausible. Nothing said "this is not a market price".
 *
 * LIVE is gone because it was never true either: the free SIX feed is 15 minutes delayed, and
 * prices are now read from the database rather than fetched during the request.
 */
public enum PriceSource {
    /** A real price from a provider. Comes with the trading day it belongs to, and a stale flag. */
    MARKET,
    /** A price the user typed. The only possibility for unlisted funds — no provider prices those. */
    MANUAL,
    /**
     * Nothing is known, so the position is shown at what it cost. The number is not false, but it
     * is not a valuation either — and the UI now says so, instead of implying a market that
     * happens not to have moved.
     */
    PURCHASE
}
