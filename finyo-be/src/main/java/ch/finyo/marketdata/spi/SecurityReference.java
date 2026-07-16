package ch.finyo.marketdata.spi;

import ch.finyo.common.money.CurrencyCode;

import java.time.OffsetDateTime;

/**
 * Vendor-neutral master data for one security.
 *
 * This is the contract between {@code ch.finyo.integration} and the rest of the
 * application. A SIX field name ({@code ProductLine}, {@code TradingBaseCurrency})
 * or an OpenFIGI one ({@code securityType2}) must never appear beyond the adapter
 * that produced it — the day SIX becomes legally unusable, exactly one package
 * changes.
 *
 * @param currency the instrument's trading currency — the field whose absence made
 *                 FX structurally impossible until now (a USD ETF was summed as CHF)
 */
public record SecurityReference(
        String isin,
        String valor,
        String ticker,
        String name,
        SecurityType type,
        CurrencyCode currency,
        String issuer,
        DataSource source,
        OffsetDateTime retrievedAt
) {
}
