package ch.finyo.integration.openfigi;

import java.util.List;

/** Wire types for the OpenFIGI v3 mapping API. Never leave this package. */
final class OpenFigiPayloads {

    private OpenFigiPayloads() {
    }

    /** Request element: {@code [{"idType":"ID_ISIN","idValue":"IE00B4L5Y983"}]} */
    record MappingRequest(String idType, String idValue) {
    }

    /**
     * Response element. A miss carries {@code warning} instead of {@code data}
     * ({@code [{"warning":"No identifier found."}]}), so {@code data} being null is
     * an expected shape, not a parse failure.
     */
    record MappingResult(List<FigiRecord> data, String warning) {
    }

    /**
     * One FIGI record. Note what is <em>not</em> here: a currency. OpenFIGI is a
     * symbology service, not a market-data feed — it maps identifiers and says
     * nothing about the trading currency or the price.
     */
    record FigiRecord(
            String figi,
            String name,
            String ticker,
            String exchCode,
            String securityType,
            String securityType2,
            String marketSector
    ) {
    }
}
