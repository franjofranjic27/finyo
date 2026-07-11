package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private static final String RESOURCE_NAME = "Position";
    private static final int PRICE_SCALE = 4;

    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentService instrumentService;

    @Transactional
    public PositionResponse create(PositionRequest request, String userId) {
        validate(request);
        log.info("Creating position isin={} valor={} for user={}", request.isin(), request.valor(), userId);

        Instrument instrument = resolveOrCreateInstrument(request, userId);
        instrument = instrumentService.refreshPriceFromSix(instrument);
        instrument = applyCurrentPriceOverride(instrument, request.currentPrice());

        Position saved = mergeOrCreatePosition(request, instrument.getId(), userId);
        log.info("Created/merged position id={} instrument={} for user={}", saved.getId(), instrument.getId(), userId);
        return PositionResponse.from(saved, instrument);
    }

    @Transactional
    public BulkImportResultResponse createBulk(PositionBulkRequest request, String userId) {
        log.info("Bulk importing {} positions for user={}", request.positions().size(), userId);
        int imported = 0;
        List<String> errors = new ArrayList<>();

        List<PositionRequest> rows = request.positions();
        for (int i = 0; i < rows.size(); i++) {
            try {
                create(rows.get(i), userId);
                imported++;
            } catch (IllegalArgumentException e) {
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }

        log.info("Bulk import finished for user={}: imported={} failed={}", userId, imported, errors.size());
        return new BulkImportResultResponse(imported, errors.size(), List.copyOf(errors));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting position id={} for user={}", id, userId);
        positionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        positionRepository.deleteById(id);
        log.info("Deleted position id={} for user={}", id, userId);
    }

    /** Bean Validation only covers the single-create path; bulk rows must report these rules per row. */
    private void validate(PositionRequest request) {
        if (isBlank(request.name()) && isBlank(request.isin()) && isBlank(request.valor())) {
            throw new IllegalArgumentException("At least one of name, isin or valor must be provided");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive number");
        }
        if (request.purchasePrice() == null || request.purchasePrice().signum() < 0) {
            throw new IllegalArgumentException("purchasePrice must be zero or positive");
        }
        if (request.currentPrice() != null && request.currentPrice().signum() < 0) {
            throw new IllegalArgumentException("currentPrice must be zero or positive");
        }
    }

    private Instrument resolveOrCreateInstrument(PositionRequest request, String userId) {
        Optional<Instrument> existing = Optional.empty();
        if (!isBlank(request.isin())) {
            existing = instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(userId, request.isin());
        }
        if (existing.isEmpty() && !isBlank(request.valor())) {
            existing = instrumentRepository.findFirstByUserIdAndValor(userId, request.valor());
        }
        return existing.orElseGet(() -> createInstrument(request, userId));
    }

    private Instrument createInstrument(PositionRequest request, String userId) {
        log.info("Auto-creating instrument isin={} valor={} for user={}", request.isin(), request.valor(), userId);
        return instrumentRepository.save(Instrument.builder()
                .userId(userId)
                .name(request.name())
                .isin(request.isin())
                .valor(request.valor())
                .instrumentType(InstrumentType.OTHER)
                .sortOrder(0)
                .build());
    }

    /** The manual price is only an override for instruments without any market price. */
    private Instrument applyCurrentPriceOverride(Instrument instrument, BigDecimal currentPrice) {
        if (currentPrice == null || instrument.getLastPrice() != null) {
            return instrument;
        }
        return instrumentRepository.save(Instrument.builder()
                .id(instrument.getId())
                .userId(instrument.getUserId())
                .valor(instrument.getValor())
                .isin(instrument.getIsin())
                .ticker(instrument.getTicker())
                .name(instrument.getName())
                .instrumentType(instrument.getInstrumentType())
                .sortOrder(instrument.getSortOrder())
                .lastPrice(currentPrice)
                .lastPriceUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private Position mergeOrCreatePosition(PositionRequest request, UUID instrumentId, String userId) {
        return positionRepository.findByUserIdAndInstrumentId(userId, instrumentId)
                .map(existing -> positionRepository.save(merge(existing, request)))
                .orElseGet(() -> positionRepository.save(Position.builder()
                        .userId(userId)
                        .instrumentId(instrumentId)
                        .quantity(request.quantity())
                        .purchasePrice(request.purchasePrice())
                        .build()));
    }

    /** Adding to an existing position merges quantities with a weighted average purchase price. */
    private Position merge(Position existing, PositionRequest request) {
        BigDecimal newQuantity = existing.getQuantity().add(request.quantity());
        BigDecimal totalCost = existing.getQuantity().multiply(existing.getPurchasePrice())
                .add(request.quantity().multiply(request.purchasePrice()));
        BigDecimal averagePrice = totalCost.divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP);

        return Position.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .instrumentId(existing.getInstrumentId())
                .quantity(newQuantity)
                .purchasePrice(averagePrice)
                .purchaseDate(existing.getPurchaseDate())
                .createdAt(existing.getCreatedAt())
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
