package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product master data plus the derived return percentages
 * ({@link Pillar3ReturnModel}) so the frontend never duplicates the formula.
 */
public record Pillar3ProductResponse(
        UUID id,
        String provider,
        String name,
        String isin,
        String valor,
        BigDecimal equityPct,
        BigDecimal terPct,
        boolean active,
        int sortOrder,
        BigDecimal expectedReturnPct,
        BigDecimal netReturnPct
) {
    public static Pillar3ProductResponse from(Pillar3Product product) {
        return new Pillar3ProductResponse(
                product.getId(),
                product.getProvider(),
                product.getName(),
                product.getIsin(),
                product.getValor(),
                product.getEquityPct(),
                product.getTerPct(),
                product.isActive(),
                product.getSortOrder(),
                Pillar3ReturnModel.grossReturnPct(product.getEquityPct()),
                Pillar3ReturnModel.netReturnPct(product.getEquityPct(), product.getTerPct())
        );
    }
}
