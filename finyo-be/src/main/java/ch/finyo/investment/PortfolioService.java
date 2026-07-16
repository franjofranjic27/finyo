package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.common.money.Money;
import ch.finyo.fx.FxConversion;
import ch.finyo.fx.FxConverter;
import ch.finyo.fx.FxRateType;
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
    private static final String CHF = CurrencyCode.CHF.value();

    /**
     * Portfolio valuation uses the mid rate, never the official sell rate — see ADR-009. The type
     * is a required argument on every conversion precisely so this choice is made explicitly here
     * rather than defaulted somewhere out of sight.
     */
    private static final FxRateType VALUATION = FxRateType.MID;

    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentFactsheetRepository factsheetRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final MarketDataService marketData;
    private final FxConverter fxConverter;

    /**
     * Aggregates the user's positions, in CHF.
     *
     * <p>The total is now built from each position's value <em>converted to CHF</em> at this
     * boundary — the single place per use case where FX happens (see ADR-009). Before, values in
     * different currencies were summed directly, so a USD ETF and a CHF one added the same way and
     * the total was simply wrong.
     *
     * <p>Two things this method still does not do, and both were bugs rather than features: it does
     * not call SIX (prices come from {@code instrument_price}, filled by
     * {@link ch.finyo.marketdata.PriceSyncJob}), and it does not write a snapshot (that is nightly
     * now, so the history is not a record of the user's logins).
     */
    public PortfolioResponse getPortfolio(String userId) {
        log.debug("Building portfolio for user={}", userId);
        List<Position> positions = positionRepository.findByUserId(userId);
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);

        if (positions.isEmpty()) {
            return new PortfolioResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, CHF, false, asOf);
        }

        List<ChfPosition> priced = priceAll(positions, userId);
        BigDecimal totalValue = sumChf(priced, ChfPosition::valueChf);
        BigDecimal totalCost = sumChf(priced, ChfPosition::costChf);
        BigDecimal gainLoss = totalValue.subtract(totalCost);
        boolean hasUnconverted = priced.stream().anyMatch(ChfPosition::unconverted);

        List<PortfolioPositionResponse> rows = priced.stream()
                .map(p -> toResponse(p, totalValue))
                .toList();

        return new PortfolioResponse(rows, totalValue, totalCost, gainLoss,
                percentOf(gainLoss, totalCost), CHF, hasUnconverted, asOf);
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

        List<ChfPosition> priced = priceAll(positionRepository.findByUserId(userId), userId);
        BigDecimal totalValue = sumChf(priced, ChfPosition::valueChf);

        ChfPosition target = priced.stream()
                .filter(p -> positionId.equals(p.priced().position().getId()))
                .findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE, positionId));
        return toDetail(target, totalValue,
                loadFactsheetInfo(target.priced().instrument().getId(), userId));
    }

    /** Called by {@link PortfolioSnapshotJob}; at most one snapshot per user and day. In CHF. */
    public void writeSnapshot(String userId) {
        List<Position> positions = positionRepository.findByUserId(userId);
        if (positions.isEmpty()) {
            return;
        }
        List<ChfPosition> priced = priceAll(positions, userId);
        snapshotRepository.upsert(userId,
                LocalDate.now(SwissTime.ZONE),
                sumChf(priced, ChfPosition::valueChf).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                sumChf(priced, ChfPosition::costChf).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
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

    private List<ChfPosition> priceAll(List<Position> positions, String userId) {
        Map<UUID, Instrument> instruments = loadInstruments(positions, userId);
        Map<String, PricePoint> prices = marketData.latestPrices(isinsOf(instruments.values()));
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        return positions.stream()
                .map(position -> convert(price(position, instruments, prices), today))
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
        // with the currency it was computed in.
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

    /**
     * Converts a position's value and cost to CHF.
     *
     * <p>The value is converted at today's rate (what it is worth now) and the cost at the rate of
     * the purchase day (what it cost in CHF then), so the CHF gain/loss reflects the real return
     * including the currency move — not just the price move. A CHF or unknown-currency position
     * passes through unchanged. A foreign one with no stored rate becomes unconverted: counted
     * nowhere in the CHF totals, and shown as such, rather than added as if it were francs.
     */
    private ChfPosition convert(PricedPosition p, LocalDate today) {
        LocalDate costDate = p.position().getPurchaseDate() != null ? p.position().getPurchaseDate() : today;
        FxConversion valueConv = fxConverter.toChf(p.value(), p.currency(), today, VALUATION);
        FxConversion costConv = fxConverter.toChf(p.cost(), p.currency(), costDate, VALUATION);

        if (valueConv instanceof FxConversion.Converted value) {
            BigDecimal costChf = costConv instanceof FxConversion.Converted cost ? cost.chf() : null;
            return new ChfPosition(p, value.chf(), costChf, value.rate(), value.rateDate(), value.type());
        }
        return new ChfPosition(p, null, null, null, null, null);
    }

    /** Metadata-only projection — the factsheet blob itself is never fetched here. */
    private PositionDetailResponse.@Nullable FactsheetInfo loadFactsheetInfo(UUID instrumentId, String userId) {
        return factsheetRepository.findMetadataByInstrumentIdAndUserId(instrumentId, userId)
                .map(PositionDetailResponse.FactsheetInfo::from)
                .orElse(null);
    }

    private PortfolioPositionResponse toResponse(ChfPosition cp, BigDecimal totalValue) {
        PricedPosition p = cp.priced();
        BigDecimal gainLoss = chfGainLoss(cp);
        return new PortfolioPositionResponse(
                p.position().getId(),
                p.position().getId(),
                p.instrument().getId(),
                p.instrument().getAssetClass(),
                p.instrument().getName(),
                p.instrument().getIsin(),
                p.instrument().getValor(),
                asString(p.currency()),
                p.position().getQuantity(),
                p.position().getPurchasePrice(),
                p.position().getPurchaseDate(),
                p.currentPrice(),
                p.priceSource(),
                p.priceAsOf(),
                p.stale(),
                p.value(),
                cp.valueChf(),
                gainLoss,
                gainLoss == null ? null : percentOf(gainLoss, cp.costChf()),
                cp.valueChf() == null ? null : percentOf(cp.valueChf(), totalValue),
                cp.fxRate(),
                cp.fxRateDate(),
                cp.fxRateType());
    }

    private PositionDetailResponse toDetail(ChfPosition cp, BigDecimal totalValue,
                                            PositionDetailResponse.@Nullable FactsheetInfo factsheet) {
        PricedPosition p = cp.priced();
        Instrument instrument = p.instrument();
        BigDecimal gainLoss = chfGainLoss(cp);
        return new PositionDetailResponse(
                p.position().getId(),
                instrument.getId(),
                instrument.getName(),
                instrument.getIsin(),
                instrument.getValor(),
                instrument.getAssetClass(),
                instrument.getTer(),
                asString(p.currency()),
                p.position().getQuantity(),
                p.position().getPurchasePrice(),
                p.position().getPurchaseDate(),
                p.currentPrice(),
                p.priceSource(),
                p.priceAsOf(),
                p.stale(),
                p.value(),
                cp.valueChf(),
                gainLoss,
                gainLoss == null ? null : percentOf(gainLoss, cp.costChf()),
                cp.valueChf() == null ? null : percentOf(cp.valueChf(), totalValue),
                cp.fxRate(),
                cp.fxRateDate(),
                cp.fxRateType(),
                instrument.getFactsheetUrl(),
                factsheet);
    }

    /** CHF gain/loss, or null when either side could not be converted. */
    private static @Nullable BigDecimal chfGainLoss(ChfPosition cp) {
        if (cp.valueChf() == null || cp.costChf() == null) {
            return null;
        }
        return cp.valueChf().subtract(cp.costChf());
    }

    /**
     * Null when nobody has established it — OpenFIGI publishes no currency, and an unlisted fund
     * may never have been resolved at all. Shown as unknown rather than asserted to be francs.
     */
    private static @Nullable String asString(@Nullable CurrencyCode currency) {
        return currency == null ? null : currency.value();
    }

    /**
     * Sums CHF amounts, skipping the unconvertible ones. Uses {@link Money} so the addition is
     * type-checked to be CHF-only: everything here is already in CHF, so it never throws — but were
     * a currency to slip in unconverted, it would fail loudly rather than produce a wrong total.
     */
    private static BigDecimal sumChf(List<ChfPosition> positions, Function<ChfPosition, BigDecimal> amount) {
        Money total = Money.zero(CurrencyCode.CHF);
        for (ChfPosition position : positions) {
            BigDecimal value = amount.apply(position);
            if (value != null) {
                total = total.plus(Money.chf(value));
            }
        }
        return total.amount();
    }

    /** part / base * 100 at scale 2 HALF_UP; zero when the base is zero. */
    private static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        if (base == null || base.signum() == 0) {
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

    /**
     * A priced position with its CHF equivalents. {@code valueChf} null marks a position that could
     * not be converted — its native value is still shown, but it is kept out of every CHF total.
     */
    private record ChfPosition(
            PricedPosition priced,
            @Nullable BigDecimal valueChf,
            @Nullable BigDecimal costChf,
            @Nullable BigDecimal fxRate,
            @Nullable LocalDate fxRateDate,
            @Nullable FxRateType fxRateType
    ) {
        boolean unconverted() {
            return valueChf == null;
        }
    }
}
