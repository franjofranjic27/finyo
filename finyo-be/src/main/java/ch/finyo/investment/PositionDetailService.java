package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Updates the instrument behind a portfolio position (master data and
 * factsheet PDF) and serves the position detail view. Factsheet PDFs are
 * stored in their own table ({@link InstrumentFactsheet}) so the blob is
 * only ever loaded by the dedicated factsheet endpoints.
 *
 * The mutating methods are deliberately NOT @Transactional: the returned
 * detail view is rebuilt via {@link PortfolioService}, which performs SIX
 * HTTP calls that must not run inside a database transaction (project
 * convention). Each single write is atomic on its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionDetailService {

    static final long MAX_FACTSHEET_BYTES = 10L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final String DEFAULT_FILENAME = "factsheet.pdf";
    private static final String POSITION_RESOURCE = "Position";
    private static final String INSTRUMENT_RESOURCE = "Instrument";
    private static final String FACTSHEET_RESOURCE = "Factsheet for position";

    private final PositionRepository positionRepository;
    private final InstrumentRepository instrumentRepository;
    private final InstrumentFactsheetRepository factsheetRepository;
    private final PortfolioService portfolioService;

    /** The read logic lives in {@link PortfolioService#getPositionDetail} (pricing-chain reuse). */
    public PositionDetailResponse getDetail(UUID positionId, String userId) {
        return portfolioService.getPositionDetail(positionId, userId);
    }

    /**
     * Applies the holding patch: quantity/purchasePrice/currentPrice only when
     * present, purchaseDate always — null clears it (see
     * {@link PositionPatchRequest} for the full contract). A present
     * currentPrice always overwrites the instrument's manual price and its
     * timestamp: the explicit user edit wins, although a later SIX refresh may
     * overwrite it again.
     *
     * <p>The position and instrument writes are two separate transactions (the
     * class is deliberately non-transactional); the instrument is loaded first
     * so a failed lookup never leaves a partially applied patch behind.
     */
    public PositionDetailResponse updatePosition(UUID positionId, PositionPatchRequest patch, String userId) {
        validate(patch);
        Position position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE, positionId));
        Instrument instrument = patch.currentPrice() == null ? null
                : instrumentRepository.findByIdAndUserId(position.getInstrumentId(), userId)
                        .orElseThrow(() ->
                                ResourceNotFoundException.of(INSTRUMENT_RESOURCE, position.getInstrumentId()));
        positionRepository.save(applyPatch(position, patch));
        if (instrument != null) {
            instrumentRepository.save(instrument.toBuilder()
                    .lastPrice(patch.currentPrice())
                    .lastPriceUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build());
        }
        log.info("Updated holding for position={} user={}", positionId, userId);
        return portfolioService.getPositionDetail(positionId, userId);
    }

    /** Applies only the non-null fields of the patch to the position's instrument. */
    public PositionDetailResponse updateInstrument(UUID positionId, InstrumentPatchRequest patch, String userId) {
        validate(patch);
        Instrument instrument = loadInstrument(positionId, userId);
        instrumentRepository.save(applyPatch(instrument, patch));
        log.info("Updated instrument details for position={} user={}", positionId, userId);
        return portfolioService.getPositionDetail(positionId, userId);
    }

    /** Stores the uploaded PDF for the position's instrument, replacing any existing factsheet. */
    public PositionDetailResponse uploadFactsheet(UUID positionId, MultipartFile file, String userId) {
        byte[] content = readValidatedPdf(file);
        Instrument instrument = loadInstrument(positionId, userId);
        factsheetRepository.upsert(instrument.getId(), userId, content,
                sanitizeFilename(file.getOriginalFilename()),
                content.length,
                OffsetDateTime.now(ZoneOffset.UTC));
        // never log file contents — only sizes and ids
        log.info("Stored factsheet ({} bytes) for position={} user={}", content.length, positionId, userId);
        return portfolioService.getPositionDetail(positionId, userId);
    }

    public FactsheetDownload getFactsheet(UUID positionId, String userId) {
        Instrument instrument = loadInstrument(positionId, userId);
        InstrumentFactsheet factsheet = factsheetRepository
                .findByInstrumentIdAndUserId(instrument.getId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of(FACTSHEET_RESOURCE, positionId));
        return new FactsheetDownload(factsheet.getFilename(), factsheet.getPdf());
    }

    public void deleteFactsheet(UUID positionId, String userId) {
        Instrument instrument = loadInstrument(positionId, userId);
        int deleted = factsheetRepository.deleteByInstrumentIdAndUserId(instrument.getId(), userId);
        if (deleted == 0) {
            throw ResourceNotFoundException.of(FACTSHEET_RESOURCE, positionId);
        }
        log.info("Deleted factsheet for position={} user={}", positionId, userId);
    }

    /** Both lookups are user-scoped — a foreign or unknown position id is always a 404. */
    private Instrument loadInstrument(UUID positionId, String userId) {
        Position position = positionRepository.findByIdAndUserId(positionId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE, positionId));
        return instrumentRepository.findByIdAndUserId(position.getInstrumentId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of(INSTRUMENT_RESOURCE, position.getInstrumentId()));
    }

    /**
     * Value rules mirror {@link PositionService}'s message style. The empty
     * check treats purchaseDate = null as "not provided" because the record
     * cannot distinguish an absent field from an explicit null — an empty
     * {@code {}} body must be a 400, so clearing the purchase date requires
     * sending at least one other field.
     */
    private static void validate(PositionPatchRequest patch) {
        if (patch.quantity() == null && patch.purchasePrice() == null
                && patch.purchaseDate() == null && patch.currentPrice() == null) {
            throw new IllegalArgumentException("at least one field must be provided");
        }
        if (patch.quantity() != null && patch.quantity().signum() <= 0) {
            throw new IllegalArgumentException("quantity must be a positive number");
        }
        if (patch.purchasePrice() != null && patch.purchasePrice().signum() < 0) {
            throw new IllegalArgumentException("purchasePrice must be zero or positive");
        }
        if (patch.currentPrice() != null && patch.currentPrice().signum() < 0) {
            throw new IllegalArgumentException("currentPrice must be zero or positive");
        }
    }

    /**
     * quantity/purchasePrice are applied only when present (NOT NULL columns,
     * never cleared); purchaseDate is always applied — the edit dialog sends
     * the full field set and uses null as an explicit clear. toBuilder()
     * preserves id, userId, instrumentId and createdAt.
     */
    private static Position applyPatch(Position position, PositionPatchRequest patch) {
        Position.PositionBuilder builder = position.toBuilder();
        if (patch.quantity() != null) {
            builder.quantity(patch.quantity());
        }
        if (patch.purchasePrice() != null) {
            builder.purchasePrice(patch.purchasePrice());
        }
        builder.purchaseDate(patch.purchaseDate());
        return builder.build();
    }

    /** Presence-only rules that Bean Validation cannot express on optional fields. */
    private static void validate(InstrumentPatchRequest patch) {
        if (patch.name() != null && patch.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (patch.factsheetUrl() != null
                && !patch.factsheetUrl().startsWith("http://")
                && !patch.factsheetUrl().startsWith("https://")) {
            throw new IllegalArgumentException("factsheetUrl must start with http:// or https://");
        }
    }

    /**
     * name/assetClass are applied only when present (they must never become
     * null); valor/ter/factsheetUrl are always applied — the edit dialog sends
     * the full field set and uses null (or blank) as an explicit clear.
     */
    private static Instrument applyPatch(Instrument instrument, InstrumentPatchRequest patch) {
        Instrument.InstrumentBuilder builder = instrument.toBuilder();
        if (patch.name() != null) {
            builder.name(patch.name());
        }
        if (patch.assetClass() != null) {
            builder.assetClass(patch.assetClass());
        }
        builder.valor(blankToNull(patch.valor()));
        builder.ter(patch.ter());
        builder.factsheetUrl(blankToNull(patch.factsheetUrl()));
        return builder.build();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static byte[] readValidatedPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Factsheet file must not be empty");
        }
        if (file.getSize() > MAX_FACTSHEET_BYTES) {
            throw new IllegalArgumentException("Factsheet must not exceed 10 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the uploaded factsheet", e);
        }
        // explicit byte check — getSize() is only a client-supplied hint
        if (content.length == 0) {
            throw new IllegalArgumentException("Factsheet file must not be empty");
        }
        if (content.length > MAX_FACTSHEET_BYTES) {
            throw new IllegalArgumentException("Factsheet must not exceed 10 MB");
        }
        if (!startsWithPdfMagic(content)) {
            throw new IllegalArgumentException("Factsheet must be a PDF file");
        }
        return content;
    }

    private static boolean startsWithPdfMagic(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Keeps only the basename of the client-supplied filename (no path
     * traversal into the Content-Disposition header), strips characters that
     * could break the header and caps the length at the column limit.
     */
    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return DEFAULT_FILENAME;
        }
        int lastSeparator = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        String basename = original.substring(lastSeparator + 1)
                .replaceAll("[\\r\\n\"]", "_")
                .trim();
        if (basename.isEmpty()) {
            return DEFAULT_FILENAME;
        }
        return basename.length() <= MAX_FILENAME_LENGTH
                ? basename
                : basename.substring(0, MAX_FILENAME_LENGTH);
    }
}
