package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single source of the modelled pillar 3a return formula:
 *
 * <pre>grossRate = 0.01 + equityPct/100 × 0.05</pre>
 *
 * i.e. a 1% base return plus a 5% equity premium weighted by the equity
 * allocation (45% equity → 3.25% p.a., 100% equity → 6% p.a.). The backend
 * exposes the derived percentages on every product response so the frontend
 * never has to duplicate this formula.
 */
final class Pillar3ReturnModel {

    private static final BigDecimal BASE_RETURN_PCT = new BigDecimal("1.00");
    private static final BigDecimal EQUITY_PREMIUM_PER_PCT = new BigDecimal("0.05");

    private Pillar3ReturnModel() {
    }

    /** Expected gross return in percent, e.g. 3.25 for 45% equity. */
    static BigDecimal grossReturnPct(BigDecimal equityPct) {
        return BASE_RETURN_PCT
            .add(equityPct.multiply(EQUITY_PREMIUM_PER_PCT))
            .setScale(2, RoundingMode.HALF_UP);
    }

    /** Expected return in percent after deducting the TER. */
    static BigDecimal netReturnPct(BigDecimal equityPct, BigDecimal terPct) {
        return grossReturnPct(equityPct).subtract(terPct).setScale(2, RoundingMode.HALF_UP);
    }

    /** Exact gross return as a fraction (e.g. 0.0325) for compounding — no display rounding. */
    static BigDecimal grossRate(BigDecimal equityPct) {
        return BASE_RETURN_PCT
            .add(equityPct.multiply(EQUITY_PREMIUM_PER_PCT))
            .movePointLeft(2);
    }
}
