package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.MarketDataService;
import ch.finyo.marketdata.PricePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String POSITION_RESOURCE = "Position";

    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentFactsheetRepository factsheetRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final MarketDataService marketData;

    /**
     * Aggregates the user's positions.
     *
     * <p>Two things this method no longer does, and both were bugs rather than features.
     *
     * <p>It no longer calls SIX. Prices come from {@code instrument_price} in Postgres, filled by
     * the nightly {@link ch.finyo.marketdata.PriceSyncJob}. Before, a portfolio read issued one
     * synchronous HTTP call per distinct instrument, inside the user's request — so a vendor that
     * accepted the connection and then went quiet would pin a Tomcat thread per position, and a
     * slow SIX made every page slow, including those with nothing to do with investments.
     *
     * <p>And it no longer writes a snapshot. A GET that mutates state is bad enough; worse, it
     * meant the performance history had a gap on every day the user did not log in, so the chart
     * was really a record of their visits. {@link PortfolioSnapshotJob} writes it nightly now.
     */
    public PortfolioResponse getPortfolio(String userId) {
        log.debug("Building portfolio for user={}", userId);
        List<Position> positions = positionRepository.findByUserId(userId);
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        if (positions.isEmpty()) {
            return new PortfolioResponse(List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, asOf);
        }

        List<PricedPosition> priced = priceAll(positions, userId);
        BigDecimal totalValue = sum(priced, PricedPosition::value);
        BigDecimal totalCost = sum(priced, PricedPosition::cost);
        BigDecimal gainLoss = totalValue.subtract(totalCost);

        List<PortfolioPositionResponse> rows = priced.stream()
                .map(p -> toResponse(p, totalValue))
                .toList();

        return new PortfolioResponse(rows, totalValue, totalCost, gainLoss,
                percentOf(gainLoss, totalCost), asOf);
    }

    /**
     * Builds the detail view of a single position. Prices every position of the user, because the
     * portfolio share needs the total — which is a database read now, not a fan-out of HTTP calls.
     *
     * @throws ResourceNotFoundException when the position does not exist or belongs to someone else
     */
    public PositionDetailResponse getPositionDetail(UUID positionId, String userId) {
        log.debug("Building position detail id={} for user={}", positionId, userId);
        positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE, positionId));

        List<PricedPosition> priced = priceAll(positionRepository.findByUserId(userId), userId);
        BigDecimal totalValue = sum(priced, PricedPosition::value);

        PricedPosition target = priced.stream()
                .filter(p -> positionId.equals(p.position().getId()))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE, positionId));
        return toDetail(target, totalValue, loadFactsheetInfo(target.instrument().getId(), userId));
    }

    /** Called by {@link PortfolioSnapshotJob}; at most one snapshot per user and day. */
    public void writeSnapshot(String userId) {
        List<Position> positions = positionRepository.findByUserId(userId);
        if (positions.isEmpty()) {
            return;
        }
        List<PricedPosition> priced = priceAll(positions, userId);
        snapshotRepository.upsert(userId,
                LocalDate.now(SwissTime.ZONE),
                sum(priced, PricedPosition::value).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                sum(priced, PricedPosition::cost).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
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

    private List<PricedPosition> priceAll(List<Position> positions, String userId) {
        Map<UUID, Instrument> instruments = loadInstruments(positions, userId);
        Map<String, PricePoint> prices = marketData.latestPrices(isinsOf(instruments.values()));
        return positions.stream()
                .map(position -> price(position, instruments, prices))
                .toList();
    }

    private static Collection<String> isinsOf(Collection<Instrument> instruments) {
        return instruments.stream()
                .map(Instrument::getIsin)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** User-scoped batch load: a foreign instrument id can never leak into the portfolio. */
    private Map<UUID, Instrument> loadInstruments(List<Position> positions, String userId) {
        List<UUID> instrumentIds = positions.stream()
                .map(Position::getInstrumentId)
                .distinct()
                .toList();
        return instrumentRepository.findByIdInAndUserId(instrumentIds, userId).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
    }

    /**
     * Market price, else the user's own, else the purchase price.
     *
     * The order is not arbitrary. A market price is a fact and beats an opinion. A manual price is
     * the only thing available for the unlisted funds that no provider quotes — which, for a Swiss
     * 3a portfolio, is most of it. And the purchase price is the last resort, valued honestly: the
     * position is shown at cost and labelled as such, rather than passed off as a market that has
     * not moved.
     */
    private PricedPosition price(Position position, Map<UUID, Instrument> instruments,
                                 Map<String, PricePoint> prices) {
        Instrument instrument = instruments.get(position.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException(
                    "Instrument " + position.getInstrumentId() + " missing for position " + position.getId());
        }

        PricePoint market = instrument.getIsin() == null ? null : prices.get(instrument.getIsin());

        BigDecimal currentPrice;
        PriceSource source;
        LocalDate priceAsOf;
        boolean stale;
        // The currency the currentPrice — and therefore the value — is actually in. For a market
        // price that is the quote's own currency, not the instrument's: the two normally agree
        // (both come from SIX), but if they ever diverge the honest thing is to label the value
        // with the currency it was computed in, not the one we hoped for. FX to a common currency
        // is PR 4; until then a foreign-currency value is shown as such rather than mislabelled.
        CurrencyCode currency;

        if (market != null) {
            currentPrice = market.price();
            source = PriceSource.MARKET;
            priceAsOf = market.asOf();
            stale = market.stale();
            currency = market.currency();
        } else if (instrument.getLastPrice() != null) {
            currentPrice = instrument.getLastPrice();
            source = PriceSource.MANUAL;
            priceAsOf = instrument.getLastPriceUpdatedAt() == null
                    ? null : instrument.getLastPriceUpdatedAt().atZoneSameInstant(SwissTime.ZONE).toLocalDate();
            stale = false;
            currency = instrument.getCurrency();
        } else {
            currentPrice = position.getPurchasePrice();
            source = PriceSource.PURCHASE;
            priceAsOf = position.getPurchaseDate();
            stale = false;
            currency = instrument.getCurrency();
        }

        BigDecimal value = position.getQuantity().multiply(currentPrice);
        BigDecimal cost = position.getQuantity().multiply(position.getPurchasePrice());
        return new PricedPosition(position, instrument, currentPrice, source, priceAsOf, stale, currency, value, cost);
    }

    /** Metadata-only projection — the factsheet blob itself is never fetched here. */
    private PositionDetailResponse.@Nullable FactsheetInfo loadFactsheetInfo(UUID instrumentId, String userId) {
        return factsheetRepository.findMetadataByInstrumentIdAndUserId(instrumentId, userId)
                .map(PositionDetailResponse.FactsheetInfo::from)
                .orElse(null);
    }

    private PortfolioPositionResponse toResponse(PricedPosition priced, BigDecimal totalValue) {
        BigDecimal gainLoss = priced.value().subtract(priced.cost());
        return new PortfolioPositionResponse(
                priced.position().getId(),
                priced.position().getId(),
                priced.instrument().getId(),
                priced.instrument().getAssetClass(),
                priced.instrument().getName(),
                priced.instrument().getIsin(),
                priced.instrument().getValor(),
                asString(priced.currency()),
                priced.position().getQuantity(),
                priced.position().getPurchasePrice(),
                priced.position().getPurchaseDate(),
                priced.currentPrice(),
                priced.priceSource(),
                priced.priceAsOf(),
                priced.stale(),
                priced.value(),
                gainLoss,
                percentOf(gainLoss, priced.cost()),
                percentOf(priced.value(), totalValue));
    }

    private PositionDetailResponse toDetail(PricedPosition priced, BigDecimal totalValue,
                                            PositionDetailResponse.@Nullable FactsheetInfo factsheet) {
        Instrument instrument = priced.instrument();
        BigDecimal gainLoss = priced.value().subtract(priced.cost());
        return new PositionDetailResponse(
                priced.position().getId(),
                instrument.getId(),
                instrument.getName(),
                instrument.getIsin(),
                instrument.getValor(),
                instrument.getAssetClass(),
                instrument.getTer(),
                asString(priced.currency()),
                priced.position().getQuantity(),
                priced.position().getPurchasePrice(),
                priced.position().getPurchaseDate(),
                priced.currentPrice(),
                priced.priceSource(),
                priced.priceAsOf(),
                priced.stale(),
                priced.value(),
                gainLoss,
                percentOf(gainLoss, priced.cost()),
                percentOf(priced.value(), totalValue),
                instrument.getFactsheetUrl(),
                factsheet);
    }

    /**
     * Null when nobody has established it — OpenFIGI publishes no currency, and an unlisted fund
     * may never have been resolved at all. Shown as unknown rather than asserted to be francs;
     * conversion to a common currency arrives with the FX module.
     */
    private static @Nullable String asString(@Nullable CurrencyCode currency) {
        return currency == null ? null : currency.value();
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
            @Nullable LocalDate priceAsOf,
            boolean stale,
            @Nullable CurrencyCode currency,
            BigDecimal value,
            BigDecimal cost
    ) {}
}
