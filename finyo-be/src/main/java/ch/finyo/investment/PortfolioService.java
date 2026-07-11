package ch.finyo.investment;

import ch.finyo.common.SwissTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private static final int PERCENT_SCALE = 2;
    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final SixMarketDataClient sixClient;

    /**
     * Aggregates all positions of the user with live prices where available.
     * Deliberately a writing read: every portfolio fetch upserts today's
     * snapshot so the performance history grows over time.
     */
    @Transactional
    public PortfolioResponse getPortfolio(String userId) {
        log.debug("Building portfolio for user={}", userId);
        List<Position> positions = positionRepository.findByUserId(userId);
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        if (positions.isEmpty()) {
            return new PortfolioResponse(List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, asOf);
        }

        Map<UUID, Instrument> instruments = loadInstruments(positions);
        Map<String, BigDecimal> livePrices = fetchLivePrices(instruments.values());

        List<PricedPosition> priced = positions.stream()
                .map(position -> price(position, instruments, livePrices))
                .toList();

        BigDecimal totalValue = sum(priced, PricedPosition::value);
        BigDecimal totalCost = sum(priced, PricedPosition::cost);
        BigDecimal gainLoss = totalValue.subtract(totalCost);

        List<PortfolioPositionResponse> rows = priced.stream()
                .map(p -> toResponse(p, totalValue))
                .toList();

        upsertSnapshot(userId, totalValue, totalCost);
        return new PortfolioResponse(rows, totalValue, totalCost, gainLoss,
                percentOf(gainLoss, totalCost), asOf);
    }

    public PortfolioHistoryResponse getHistory(String userId, int months) {
        log.debug("Fetching portfolio history for user={} months={}", userId, months);
        LocalDate from = LocalDate.now(SwissTime.ZONE).minusMonths(months);
        List<PortfolioHistoryPoint> points = snapshotRepository
                .findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(userId, from)
                .stream()
                .map(s -> new PortfolioHistoryPoint(s.getSnapshotDate(), s.getTotalValue(), s.getTotalCost()))
                .toList();
        return new PortfolioHistoryResponse(points);
    }

    private Map<UUID, Instrument> loadInstruments(List<Position> positions) {
        List<UUID> instrumentIds = positions.stream()
                .map(Position::getInstrumentId)
                .distinct()
                .toList();
        return instrumentRepository.findAllById(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
    }

    /** One SIX call per distinct identifier; the Caffeine cache absorbs repeats across requests. */
    private Map<String, BigDecimal> fetchLivePrices(Collection<Instrument> instruments) {
        Map<String, BigDecimal> prices = new HashMap<>();
        instruments.stream()
                .map(Instrument::preferredIdentifier)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(identifier -> sixClient.fetchByValorOrIsin(identifier)
                        .map(MarketDataResponse::lastPrice)
                        .ifPresent(price -> prices.put(identifier, price)));
        return prices;
    }

    /** Price fallback chain: SIX live -> persisted instrument price -> purchase price. */
    private PricedPosition price(Position position, Map<UUID, Instrument> instruments,
                                 Map<String, BigDecimal> livePrices) {
        Instrument instrument = instruments.get(position.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException(
                    "Instrument " + position.getInstrumentId() + " missing for position " + position.getId());
        }

        String identifier = instrument.preferredIdentifier();
        BigDecimal livePrice = identifier != null ? livePrices.get(identifier) : null;

        BigDecimal currentPrice;
        PriceSource priceSource;
        if (livePrice != null) {
            currentPrice = livePrice;
            priceSource = PriceSource.LIVE;
        } else if (instrument.getLastPrice() != null) {
            currentPrice = instrument.getLastPrice();
            priceSource = PriceSource.CACHE;
        } else {
            currentPrice = position.getPurchasePrice();
            priceSource = PriceSource.PURCHASE;
        }

        BigDecimal value = position.getQuantity().multiply(currentPrice);
        BigDecimal cost = position.getQuantity().multiply(position.getPurchasePrice());
        return new PricedPosition(position, instrument, currentPrice, priceSource, value, cost);
    }

    private PortfolioPositionResponse toResponse(PricedPosition priced, BigDecimal totalValue) {
        BigDecimal gainLoss = priced.value().subtract(priced.cost());
        return new PortfolioPositionResponse(
                priced.position().getId(),
                priced.instrument().getId(),
                priced.instrument().getName(),
                priced.instrument().getIsin(),
                priced.instrument().getValor(),
                priced.position().getQuantity(),
                priced.position().getPurchasePrice(),
                priced.currentPrice(),
                priced.priceSource(),
                priced.value(),
                gainLoss,
                percentOf(gainLoss, priced.cost()),
                percentOf(priced.value(), totalValue));
    }

    /**
     * At most one snapshot per user and day: update today's row when present,
     * otherwise insert. A concurrent first request (e.g. React StrictMode
     * double fetch) may win the insert race — the unique constraint surfaces
     * that via saveAndFlush and the write is retried as an update.
     */
    private void upsertSnapshot(String userId, BigDecimal totalValue, BigDecimal totalCost) {
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        BigDecimal value = totalValue.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cost = totalCost.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        Optional<PortfolioSnapshot> existing = snapshotRepository.findByUserIdAndSnapshotDate(userId, today);
        if (existing.isPresent()) {
            snapshotRepository.save(withTotals(existing.get(), value, cost));
            return;
        }
        try {
            snapshotRepository.saveAndFlush(PortfolioSnapshot.builder()
                    .userId(userId)
                    .snapshotDate(today)
                    .totalValue(value)
                    .totalCost(cost)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.debug("Snapshot insert race for user={} date={}, retrying as update", userId, today);
            snapshotRepository.findByUserIdAndSnapshotDate(userId, today)
                    .map(snapshot -> withTotals(snapshot, value, cost))
                    .ifPresent(snapshotRepository::save);
        }
    }

    private PortfolioSnapshot withTotals(PortfolioSnapshot snapshot, BigDecimal totalValue, BigDecimal totalCost) {
        return PortfolioSnapshot.builder()
                .id(snapshot.getId())
                .userId(snapshot.getUserId())
                .snapshotDate(snapshot.getSnapshotDate())
                .totalValue(totalValue)
                .totalCost(totalCost)
                .createdAt(snapshot.getCreatedAt())
                .build();
    }

    private static BigDecimal sum(List<PricedPosition> priced, Function<PricedPosition, BigDecimal> amount) {
        return priced.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** part / base * 100 at scale 2 HALF_UP; zero when the base is zero. */
    private static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private record PricedPosition(
            Position position,
            Instrument instrument,
            BigDecimal currentPrice,
            PriceSource priceSource,
            BigDecimal value,
            BigDecimal cost
    ) {}
}
