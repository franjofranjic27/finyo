package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.SecurityReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentService {

    private static final String RESOURCE_NAME = "Instrument";

    private final InstrumentRepository instrumentRepository;
    private final InstrumentFactory instrumentFactory;

    /**
     * Previews what the providers know about an ISIN or valor, without creating anything, for the
     * add-position form's live lookup. A read against the provider chain (Postgres-cached), so
     * repeated keystrokes on the same identifier do not hammer the vendors.
     */
    public InstrumentLookupResponse lookup(String isin, String valor) {
        SourceResult<SecurityReference> result = instrumentFactory.lookup(isin, valor);
        return switch (result) {
            case SourceResult.Found<SecurityReference>(SecurityReference ref) -> new InstrumentLookupResponse(
                    InstrumentLookupResponse.Status.FOUND,
                    ref.name(),
                    ref.ticker(),
                    ref.currency() == null ? null : ref.currency().value(),
                    InstrumentFactory.assetClassFor(ref));
            case SourceResult.NotFound<SecurityReference> _ -> InstrumentLookupResponse.notFound();
            case SourceResult.Unavailable<SecurityReference> _ -> InstrumentLookupResponse.unavailable();
        };
    }

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

}
