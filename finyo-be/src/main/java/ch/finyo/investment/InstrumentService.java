package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private static final String RESOURCE_NAME = "Instrument";

    private final InstrumentRepository instrumentRepository;
    private final SixMarketDataClient sixClient;

    public List<InstrumentResponse> getAll(String userId) {
        log.debug("Fetching all instruments for user={}", userId);
        return instrumentRepository.findByUserIdOrderBySortOrderAscNameAsc(userId)
                .stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    public InstrumentResponse getById(UUID id, String userId) {
        log.debug("Fetching instrument id={} for user={}", id, userId);
        return instrumentRepository.findByIdAndUserId(id, userId)
                .map(InstrumentResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
    }

    @Transactional
    public InstrumentResponse create(InstrumentRequest request, String userId) {
        log.info("Creating instrument name='{}' type={} for user={}", request.name(), request.instrumentType(), userId);
        var instrument = Instrument.builder()
                .userId(userId)
                .valor(request.valor())
                .isin(request.isin())
                .ticker(request.ticker())
                .name(request.name())
                .instrumentType(request.instrumentType())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        Instrument saved = instrumentRepository.save(instrument);
        log.info("Created instrument id={} for user={}", saved.getId(), userId);
        return InstrumentResponse.from(saved);
    }

    @Transactional
    public InstrumentResponse update(UUID id, InstrumentRequest request, String userId) {
        log.info("Updating instrument id={} for user={}", id, userId);
        var existing = instrumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        // toBuilder keeps every field not covered by the request (prices,
        // asset class, factsheet, …) instead of silently nulling it
        var updated = existing.toBuilder()
                .valor(request.valor() != null ? request.valor() : existing.getValor())
                .isin(request.isin() != null ? request.isin() : existing.getIsin())
                .ticker(request.ticker() != null ? request.ticker() : existing.getTicker())
                .name(request.name() != null ? request.name() : existing.getName())
                .instrumentType(request.instrumentType() != null ? request.instrumentType() : existing.getInstrumentType())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : existing.getSortOrder())
                .build();

        Instrument saved = instrumentRepository.save(updated);
        log.info("Updated instrument id={} for user={}", saved.getId(), userId);
        return InstrumentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting instrument id={} for user={}", id, userId);
        instrumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        instrumentRepository.deleteById(id);
        log.info("Deleted instrument id={} for user={}", id, userId);
    }

    public MarketDataResponse getMarketData(UUID id, String userId) {
        log.debug("Fetching market data for instrument id={} user={}", id, userId);
        var instrument = instrumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        String identifier = instrument.preferredIdentifier();

        if (identifier != null) {
            Optional<MarketDataResponse> liveData = sixClient.fetchByValorOrIsin(identifier);
            if (liveData.isPresent()) {
                updateCachedPrice(instrument, liveData.get());
                return liveData.get();
            }
        }

        // Fallback to last known price when SIX is unavailable
        if (instrument.getLastPrice() != null) {
            log.info("SIX unavailable, returning cached price for instrument id={}", id);
            return buildFallbackResponse(instrument);
        }

        throw new IllegalStateException(
                "No market data available for instrument " + id
                        + ". Please add a valid VALOR, ISIN, or ticker.");
    }

    /**
     * Fetches live data from SIX for the instrument's preferred identifier and
     * persists the fresh price (and SIX name) via {@link #updateCachedPrice}.
     * Package-private so PositionService can reuse it during position creation.
     *
     * @return the refreshed instrument, or the unchanged one when the
     *         instrument has no identifier or SIX returned no data
     */
    Instrument refreshPriceFromSix(Instrument instrument) {
        String identifier = instrument.preferredIdentifier();
        if (identifier == null) {
            return instrument;
        }
        return sixClient.fetchByValorOrIsin(identifier)
                .map(data -> updateCachedPrice(instrument, data))
                .orElse(instrument);
    }

    // No @Transactional here: the single repository save() runs in its own
    // transaction, and a self-invocation would bypass the Spring proxy anyway.
    private Instrument updateCachedPrice(Instrument instrument, MarketDataResponse data) {
        if (data.lastPrice() == null) {
            return instrument;
        }
        try {
            var updated = instrument.toBuilder()
                    .name(data.name() != null && !data.name().isBlank() ? data.name() : instrument.getName())
                    .lastPrice(data.lastPrice())
                    .lastPriceUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            return instrumentRepository.save(updated);
        } catch (Exception e) {
            log.warn("Failed to update cached price for instrument id={}", instrument.getId(), e);
            return instrument;
        }
    }

    private MarketDataResponse buildFallbackResponse(Instrument instrument) {
        return new MarketDataResponse(
                instrument.getValor(),
                instrument.getIsin(),
                instrument.getTicker(),
                instrument.getName(),
                null,
                "CHF",
                instrument.getLastPrice(),
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                instrument.getLastPriceUpdatedAt(),
                true
        );
    }
}
