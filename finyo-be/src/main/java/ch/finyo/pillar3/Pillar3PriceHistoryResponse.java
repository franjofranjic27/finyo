package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The stored daily closes for a pillar 3a product's fund.
 *
 * <p>Deliberately its own record rather than a reuse of
 * {@code ch.finyo.investment.PriceHistoryResponse}: the shape is identical, but importing it
 * would couple the pillar3 module to the investment module for a three-field DTO. The market
 * data itself is shared either way — both endpoints read the same tenant-free
 * {@code instrument_price} table.
 *
 * @param currency the currency the closes are in, or null when no history is stored yet.
 *                 Taken from the stored prices themselves — a fund trades in one currency
 *                 across its whole history.
 * @param points   oldest first; empty when the fund is unlisted (no provider quotes it, which
 *                 is normal for 3a funds) or its backfill has not run yet
 */
public record Pillar3PriceHistoryResponse(String isin, String currency, List<PriceHistoryPoint> points) {

    public record PriceHistoryPoint(LocalDate date, BigDecimal close) {
    }
}
