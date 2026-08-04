package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SwissTime;
import ch.finyo.marketdata.MarketDataService;
import ch.finyo.marketdata.PricePoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Serves the price history for a catalog product's fund, read from the shared
 * {@code instrument_price} table via {@link MarketDataService} — the read never touches the
 * network (marketdata module convention).
 *
 * <p>Pillar 3a funds are not held as portfolio positions, so neither the nightly
 * {@code PriceSyncJob} nor position creation ever backfills them. This service closes that gap
 * on first view: when no history is stored yet, it fires the same
 * {@link MarketDataService#backfill} that position creation uses — but asynchronously, so a
 * slow or dead provider can never delay the response. A listed fund fills on first view and
 * shows its chart on the next open; an <b>unlisted 3a fund legitimately stays empty forever</b>
 * (no provider quotes it), which the UI states rather than hides.
 */
@Slf4j
@Service
public class Pillar3ProductPriceHistoryService {

    private static final String RESOURCE_NAME = "Pillar3Product";

    /** How much history the chart asks for — matches the marketdata backfill window. */
    private static final int HISTORY_YEARS = 3;

    private final Pillar3ProductRepository productRepository;
    private final MarketDataService marketData;
    private final Executor backfillExecutor;

    /**
     * ISINs with a backfill currently in flight. Dialog-open is a user-frequent trigger —
     * unlike the fx catch-up's rare startup/create triggers — so concurrent opens of the same
     * (typically unlisted, forever-empty) fund must collapse into one provider call instead of
     * one per viewer. Completed attempts are removed again: a fund can get listed later, and
     * the client's query staleTime already damps same-user repeats.
     */
    private final Set<String> backfillsInFlight = ConcurrentHashMap.newKeySet();

    @Autowired
    public Pillar3ProductPriceHistoryService(Pillar3ProductRepository productRepository,
                                             MarketDataService marketData) {
        // One virtual thread per backfill: rare, network-bound, short-lived — the same
        // fire-and-forget pattern as the fx catch-up (FxRateCatchUpService).
        this(productRepository, marketData, Thread::startVirtualThread);
    }

    Pillar3ProductPriceHistoryService(Pillar3ProductRepository productRepository,
                                      MarketDataService marketData,
                                      Executor backfillExecutor) {
        this.productRepository = productRepository;
        this.marketData = marketData;
        this.backfillExecutor = backfillExecutor;
    }

    /**
     * The stored daily closes for the product's fund, oldest first, over the last three years.
     * The product may be inactive — a scenario keeps its product reference even after an admin
     * deactivates it, and the detail view must still resolve.
     *
     * @throws ResourceNotFoundException when no product with the given id exists
     */
    public Pillar3PriceHistoryResponse getPriceHistory(UUID productId) {
        Pillar3Product product = productRepository.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, productId));

        String isin = product.getIsin();
        // Defensive: the column is NOT NULL, but an ISIN-less product must mean "no history",
        // never a provider call with a blank identifier.
        if (isin == null || isin.isBlank()) {
            return new Pillar3PriceHistoryResponse(null, null, List.of());
        }

        LocalDate from = LocalDate.now(SwissTime.ZONE).minusYears(HISTORY_YEARS);
        List<PricePoint> points = marketData.priceHistory(isin, from);
        if (points.isEmpty()) {
            triggerBackfill(isin);
            return new Pillar3PriceHistoryResponse(isin, null, List.of());
        }

        // A fund trades in one currency across its whole history, so the first stored
        // close carries the currency for the entire response.
        String currency = points.getFirst().currency() == null
                ? null
                : points.getFirst().currency().value();
        List<Pillar3PriceHistoryResponse.PriceHistoryPoint> mapped = points.stream()
                .map(p -> new Pillar3PriceHistoryResponse.PriceHistoryPoint(p.asOf(), p.price()))
                .toList();
        return new Pillar3PriceHistoryResponse(isin, currency, mapped);
    }

    /** Fire-and-forget: a failed backfill is only logged — a later view simply retries it. */
    private void triggerBackfill(String isin) {
        if (!backfillsInFlight.add(isin)) {
            return; // someone is already fetching this fund's history
        }
        backfillExecutor.execute(() -> {
            try {
                int stored = marketData.backfill(isin);
                log.info("Pillar3 first-view backfill for {} stored {} closes", isin, stored);
            } catch (RuntimeException e) {
                log.warn("Pillar3 first-view backfill for {} failed: {}", isin, e.getMessage(), e);
            } finally {
                backfillsInFlight.remove(isin);
            }
        });
    }
}
