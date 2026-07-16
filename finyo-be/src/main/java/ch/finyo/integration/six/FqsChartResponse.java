package ch.finyo.integration.six;

import ch.finyo.marketdata.spi.PriceBar;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw SIX charts.json payload.
 *
 * Unlike ref.json and movie.json, charts.json is not column-oriented — it nests parallel arrays
 * under each valor:
 *
 * <pre>
 * {"valors":[{"ISIN":"IE00B4L5Y983","data":{
 *     "Date":  [20260105, 20260106, ...],
 *     "Close": [144.20,   143.94,   ...]}}]}
 * </pre>
 *
 * {@code Date[i]} and {@code Close[i]} belong together. This type exists so that shape stays
 * inside {@code ch.finyo.integration.six}.
 */
record FqsChartResponse(List<Valor> valors) {

    private static final DateTimeFormatter FQS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    record Valor(Data data) {
    }

    /**
     * charts.json capitalises its keys ({@code "Date"}, {@code "Close"}) while the record
     * components are lower-case, and Jackson 3 matches property names case-sensitively — so the
     * mapping has to be spelled out. Without it both arrays deserialise to null and every
     * backfill silently stores no history at all.
     */
    record Data(@JsonProperty("Date") List<Long> date, @JsonProperty("Close") List<BigDecimal> close) {
    }

    /**
     * Zips the parallel Date/Close arrays into bars. A bar with no close is dropped rather than
     * carried as a zero — a zero close would flow into a chart and a valuation as a real number.
     * An empty or absent payload yields an empty list: the security may simply have no closes in
     * the window, which is a legitimate answer, not a failure.
     */
    List<PriceBar> toBars() {
        if (valors == null || valors.isEmpty()) {
            return List.of();
        }
        Data data = valors.getFirst().data();
        if (data == null || data.date() == null || data.close() == null) {
            return List.of();
        }
        int count = Math.min(data.date().size(), data.close().size());
        List<PriceBar> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Long day = data.date().get(i);
            BigDecimal close = data.close().get(i);
            if (day == null || close == null || close.signum() <= 0) {
                continue;
            }
            bars.add(new PriceBar(parseDay(day), close));
        }
        return bars;
    }

    private static LocalDate parseDay(long yyyymmdd) {
        return LocalDate.parse(Long.toString(yyyymmdd), FQS_DATE);
    }
}
