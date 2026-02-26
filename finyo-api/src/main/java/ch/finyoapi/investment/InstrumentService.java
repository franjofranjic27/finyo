package ch.finyoapi.investment;

import ch.finyoapi.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

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
                .orElseThrow(() -> ResourceNotFoundException.of("Instrument", id));
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
                .orElseThrow(() -> ResourceNotFoundException.of("Instrument", id));

        var updated = Instrument.builder()
                .id(existing.getId())
                .userId(userId)
                .valor(request.valor() != null ? request.valor() : existing.getValor())
                .isin(request.isin() != null ? request.isin() : existing.getIsin())
                .ticker(request.ticker() != null ? request.ticker() : existing.getTicker())
                .name(request.name() != null ? request.name() : existing.getName())
                .instrumentType(request.instrumentType() != null ? request.instrumentType() : existing.getInstrumentType())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : existing.getSortOrder())
                .lastPrice(existing.getLastPrice())
                .lastPriceUpdatedAt(existing.getLastPriceUpdatedAt())
                .build();

        Instrument saved = instrumentRepository.save(updated);
        log.info("Updated instrument id={} for user={}", saved.getId(), userId);
        return InstrumentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting instrument id={} for user={}", id, userId);
        instrumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Instrument", id));
        instrumentRepository.deleteById(id);
        log.info("Deleted instrument id={} for user={}", id, userId);
    }

    public MarketDataResponse getMarketData(UUID id, String userId) {
        log.debug("Fetching market data for instrument id={} user={}", id, userId);
        var instrument = instrumentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Instrument", id));

        // Prefer valor, fall back to ISIN, then ticker
        String identifier = instrument.getValor() != null ? instrument.getValor()
                : instrument.getIsin() != null ? instrument.getIsin()
                : instrument.getTicker();

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

    @Transactional
    protected void updateCachedPrice(Instrument instrument, MarketDataResponse data) {
        if (data.lastPrice() == null) {
            return;
        }
        try {
            var updated = Instrument.builder()
                    .id(instrument.getId())
                    .userId(instrument.getUserId())
                    .valor(instrument.getValor())
                    .isin(instrument.getIsin())
                    .ticker(instrument.getTicker())
                    .name(data.name() != null && !data.name().isBlank() ? data.name() : instrument.getName())
                    .instrumentType(instrument.getInstrumentType())
                    .sortOrder(instrument.getSortOrder())
                    .lastPrice(data.lastPrice())
                    .lastPriceUpdatedAt(OffsetDateTime.now())
                    .build();
            instrumentRepository.save(updated);
        } catch (Exception e) {
            log.warn("Failed to update cached price for instrument id={}", instrument.getId(), e);
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
