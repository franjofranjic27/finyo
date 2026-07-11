package ch.finyo.investment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single source of truth for the asset-class heuristics — the SQL backfill in
 * V21__add_instrument_details.sql applies the same rules and must stay in sync.
 */
class AssetClassifierTest {

    @ParameterizedTest
    @CsvSource({
            "iShares Core MSCI World ETF, ETF",
            "vanguard etf acc,            ETF",
            "UBS Strategy Fund,           FUND",
            "Swisscanto Anlagefonds,      FUND",
            "Kassenobligation 2030,       BOND",
            "Corporate Bond 2028,         BOND",
            "Festgeld 12 Monate,          BOND",
            "Nestlé SA,                   STOCK",
            "Roche Holding AG,            STOCK",
    })
    void classifies_by_name_keywords(String name, AssetClass expected) {
        assertThat(AssetClassifier.classify(name, null)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"BTC", "ETH", "Bitcoin", "ETHEREUM", "Dogecoin"})
    void classifies_crypto_when_no_isin_is_present(String name) {
        assertThat(AssetClassifier.classify(name, null)).isEqualTo(AssetClass.CRYPTO);
    }

    @Test
    void a_crypto_looking_name_with_an_isin_is_not_crypto() {
        assertThat(AssetClassifier.classify("BTC", "CH0038863350")).isEqualTo(AssetClass.STOCK);
        assertThat(AssetClassifier.classify("Coinshares Something", "CH0038863350")).isEqualTo(AssetClass.STOCK);
    }

    @Test
    void a_blank_isin_counts_as_no_isin() {
        assertThat(AssetClassifier.classify("BTC", " ")).isEqualTo(AssetClass.CRYPTO);
    }

    @Test
    void etf_wins_over_fund_and_fund_wins_over_bond() {
        assertThat(AssetClassifier.classify("Bond ETF Fund", null)).isEqualTo(AssetClass.ETF);
        assertThat(AssetClassifier.classify("Bond Fund", null)).isEqualTo(AssetClass.FUND);
    }

    @Test
    void null_and_blank_names_default_to_stock() {
        assertThat(AssetClassifier.classify(null, null)).isEqualTo(AssetClass.STOCK);
        assertThat(AssetClassifier.classify("  ", null)).isEqualTo(AssetClass.STOCK);
    }
}
