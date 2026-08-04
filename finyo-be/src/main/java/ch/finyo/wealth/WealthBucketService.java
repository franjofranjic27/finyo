package ch.finyo.wealth;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CRUD for the user's manual wealth buckets. Portfolio and pillar 3a rows are
 * no longer persisted — {@link WealthOverviewService} synthesizes them live
 * from their modules — so create and update accept MANUAL requests only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WealthBucketService {

    private static final String RESOURCE_NAME = "WealthBucket";

    private final WealthBucketRepository bucketRepository;

    @Transactional
    public WealthBucketResponse create(WealthBucketRequest request, String userId) {
        log.info("Creating wealth bucket name='{}' for user={}", request.name(), userId);
        validateManualRequest(request);
        if (bucketRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException("A wealth bucket named '" + request.name() + "' already exists");
        }

        WealthBucket saved = bucketRepository.save(toEntity(request, userId, null));
        log.info("Created wealth bucket id={} for user={}", saved.getId(), userId);
        return WealthBucketResponse.from(saved);
    }

    @Transactional
    public WealthBucketResponse update(UUID id, WealthBucketRequest request, String userId) {
        log.info("Updating wealth bucket id={} for user={}", id, userId);
        WealthBucket existing = bucketRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        validateManualRequest(request);
        if (bucketRepository.existsByUserIdAndNameAndIdNot(userId, request.name(), id)) {
            throw new IllegalArgumentException("A wealth bucket named '" + request.name() + "' already exists");
        }

        WealthBucket saved = bucketRepository.save(toEntity(request, userId, existing));
        log.info("Updated wealth bucket id={} for user={}", saved.getId(), userId);
        return WealthBucketResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting wealth bucket id={} for user={}", id, userId);
        WealthBucket existing = bucketRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        bucketRepository.delete(existing);
        log.info("Deleted wealth bucket id={} for user={}", id, userId);
    }

    /** Only MANUAL buckets are persisted; other sources appear automatically in the overview. */
    private static void validateManualRequest(WealthBucketRequest request) {
        if (request.source() != WealthSource.MANUAL) {
            throw new IllegalArgumentException("Only MANUAL wealth buckets can be saved — "
                    + request.source() + " rows are derived automatically from their module");
        }
        if (request.manualBalance() == null) {
            throw new IllegalArgumentException("manualBalance is required for MANUAL wealth buckets");
        }
        if (request.assetClasses() != null && !request.assetClasses().isEmpty()) {
            throw new IllegalArgumentException("assetClasses must be empty for MANUAL wealth buckets");
        }
    }

    private static WealthBucket toEntity(WealthBucketRequest request, String userId,
                                         @Nullable WealthBucket existing) {
        return WealthBucket.builder()
                .id(existing != null ? existing.getId() : null)
                .userId(userId)
                .name(request.name())
                .note(request.note())
                .source(WealthSource.MANUAL)
                .manualBalance(request.manualBalance())
                .monthlyRate(request.monthlyRate() != null ? request.monthlyRate() : BigDecimal.ZERO)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .createdAt(existing != null ? existing.getCreatedAt() : null)
                .build();
    }
}
