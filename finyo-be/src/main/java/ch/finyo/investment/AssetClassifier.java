package ch.finyo.investment;

import java.util.Locale;
import java.util.Set;

/**
 * Heuristic asset-class detection for auto-created instruments.
 *
 * Single source of truth for the classification rules in code; the one-time
 * backfill in V21__add_instrument_details.sql applies the same rules in SQL
 * and must be kept in sync. Rule order matters: ETF wins over FUND, FUND over
 * CRYPTO, CRYPTO over BOND; everything else is a STOCK.
 */
public final class AssetClassifier {

    private static final Set<String> CRYPTO_NAMES = Set.of("BTC", "ETH", "BITCOIN", "ETHEREUM");

    private AssetClassifier() {
    }

    public static AssetClass classify(String name, String isin) {
        if (name == null || name.isBlank()) {
            return AssetClass.STOCK;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        if (lowerName.contains("etf")) {
            return AssetClass.ETF;
        }
        if (lowerName.contains("fund") || lowerName.contains("fonds")) {
            return AssetClass.FUND;
        }
        // Crypto has no ISIN — a listed instrument named "…coin" is not a coin.
        boolean hasIsin = isin != null && !isin.isBlank();
        if (!hasIsin && (CRYPTO_NAMES.contains(name.toUpperCase(Locale.ROOT)) || lowerName.contains("coin"))) {
            return AssetClass.CRYPTO;
        }
        if (lowerName.contains("obligation") || lowerName.contains("bond") || lowerName.contains("festgeld")) {
            return AssetClass.BOND;
        }
        return AssetClass.STOCK;
    }
}
