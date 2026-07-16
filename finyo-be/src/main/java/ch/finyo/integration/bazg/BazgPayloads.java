package ch.finyo.integration.bazg;

import java.util.List;

/**
 * Raw BAZG payload, kept inside the adapter package.
 *
 * <pre>
 * {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"EUR","rate":"0.93661"}, ...]}
 * </pre>
 *
 * The rate is CHF per one unit already — no inversion — and arrives as a <em>string</em>, parsed
 * to a decimal in the adapter. It is the customs sell rate, distinctly higher than the ECB mid
 * rate; the domain files it under {@link ch.finyo.fx.FxRateType#OFFICIAL_CH} so it is never mixed
 * into a valuation.
 */
record BazgPayload(String date, String base, List<Rate> rates) {

    record Rate(String symbol, String rate) {}
}
