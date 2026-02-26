package ch.finyoapi.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketDataResponse(
        String valor,
        String isin,
        String ticker,
        String name,
        String exchange,
        String currency,
        BigDecimal lastPrice,
        BigDecimal change1d,
        Double change1dPct,
        BigDecimal change1wPct,
        BigDecimal change1mPct,
        BigDecimal changeYtdPct,
        BigDecimal change1yPct,
        BigDecimal high52w,
        BigDecimal low52w,
        BigDecimal marketCap,
        BigDecimal dividendYield,
        String instrumentType,
        String sector,
        BigDecimal ter,
        OffsetDateTime dataAsOf,
        boolean fromCache
) {}
