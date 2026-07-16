package ch.finyo.fx;

/**
 * Which kind of exchange rate a stored value is — and the reason it is a required parameter
 * everywhere, never a default.
 *
 * The two differ by more than rounding. On the same day the ECB mid rate and the Swiss customs
 * (BAZG) sell rate for EUR/CHF sat about 1.2 % apart. On a six-figure portfolio that is real
 * money, and using one where the other belongs is a systematic error, not noise. A default
 * overload would hide exactly the decision that must be made deliberately. See ADR-009.
 */
public enum FxRateType {

    /** ECB reference (mid-market) rate, via Frankfurter. For valuation and charts. */
    MID,

    /**
     * The official Swiss sell rate published by the Federal Customs Office (BAZG). Higher than the
     * mid rate — it is a sell rate — so it belongs only in tax contexts that prescribe it, never in
     * portfolio valuation.
     */
    OFFICIAL_CH
}
