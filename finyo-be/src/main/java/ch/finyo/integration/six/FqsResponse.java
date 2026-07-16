package ch.finyo.integration.six;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Raw SIX FQS payload.
 *
 * FQS answers column-oriented, not object-oriented — {@code colNames} names the
 * columns and {@code rowData} carries parallel arrays:
 *
 * <pre>
 * {"totalRows":1,
 *  "colNames":["ISIN","ValorNumber","ValorSymbol","ShortName","ProductLine","TradingBaseCurrency"],
 *  "rowData":[["CH0038863350",3886335,"NESN","NESTLE N","BC","CHF"]]}
 * </pre>
 *
 * Reading a row therefore means resolving a column name to its index first. This
 * type exists so that quirk stays inside {@code ch.finyo.integration.six}.
 *
 * {@code totalRows: 0} is a normal answer, not an error: it means the instrument
 * is not listed on SIX. The CSIF funds behind VIAC and finpension answer exactly
 * that — they are unlisted institutional share classes (verified).
 */
record FqsResponse(
        Integer totalRows,
        List<String> colNames,
        List<List<Object>> rowData
) {

    boolean isEmpty() {
        return totalRows == null || totalRows == 0 || rowData == null || rowData.isEmpty();
    }

    /**
     * First row as a column-name → value map, or empty when SIX knows no such security.
     *
     * @throws IllegalStateException when SIX reports rows but sends no column names. That
     *         payload parses, and it is meaningless — which is the dangerous combination:
     *         reporting it as "security not found" would let a SIX format change quietly
     *         freeze every instrument as a name-based guess. It is a vendor failure, so it
     *         is thrown, becomes Unavailable, and counts towards the circuit breaker.
     */
    Optional<Map<String, Object>> firstRow() {
        if (isEmpty()) {
            return Optional.empty();
        }
        if (colNames == null || colNames.isEmpty()) {
            throw new IllegalStateException("SIX reported " + totalRows + " rows but sent no column names");
        }
        List<Object> row = rowData.getFirst();
        return Optional.of(IntStream.range(0, Math.min(colNames.size(), row.size()))
                .boxed()
                .filter(i -> row.get(i) != null)
                .collect(java.util.stream.Collectors.toMap(colNames::get, row::get)));
    }
}
