package ch.finyo.marketdata.spi;

/**
 * Instrument type as the market-data domain knows it.
 *
 * Deliberately not {@code ch.finyo.investment.InstrumentType}: marketdata is a
 * standalone module and must not depend on a feature module. The investment
 * module maps this onto its own enums — that mapping is its business, not ours.
 */
public enum SecurityType {
    EQUITY,
    ETF,
    FUND,
    BOND,
    CRYPTO,
    OTHER
}
