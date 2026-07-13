package ch.finyo.wealth;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.investment.AssetClass;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for wealth buckets, including the source-dependent presence rules and
 * two uniqueness invariants that prevent double counting in the overview:
 * each asset class is linked to at most one bucket per user, and at most one
 * PILLAR3 bucket exists per user (there is only one default pillar 3a
 * scenario whose balance would otherwise be counted twice).
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
        validateSourceFields(request, userId, null);
        if (bucketRepository.existsByUserIdAndName(userId, request.name())) {
            throw new IllegalArgumentException("A wealth bucket named '" + request.name() + "' already exists");
        }
        validateAssetClassesNotLinkedElsewhere(request, userId, null);

        WealthBucket saved = bucketRepository.save(toEntity(request, userId, null));
        log.info("Created wealth bucket id={} for user={}", saved.getId(), userId);
        return WealthBucketResponse.from(saved);
    }

    @Transactional
    public WealthBucketResponse update(UUID id, WealthBucketRequest request, String userId) {
        log.info("Updating wealth bucket id={} for user={}", id, userId);
        WealthBucket existing = bucketRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        validateSourceFields(request, userId, id);
        if (bucketRepository.existsByUserIdAndNameAndIdNot(userId, request.name(), id)) {
            throw new IllegalArgumentException("A wealth bucket named '" + request.name() + "' already exists");
        }
        validateAssetClassesNotLinkedElsewhere(request, userId, id);

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

    /** Per-source presence rules; excludeId skips the bucket being updated. */
    private void validateSourceFields(WealthBucketRequest request, String userId, UUID excludeId) {
        switch (request.source()) {
            case MANUAL -> {
                if (request.manualBalance() == null) {
                    throw new IllegalArgumentException("manualBalance is required for MANUAL wealth buckets");
                }
                requireNoAssetClasses(request);
            }
            case PORTFOLIO -> {
                if (request.assetClasses() == null || request.assetClasses().isEmpty()) {
                    throw new IllegalArgumentException("assetClasses must not be empty for PORTFOLIO wealth buckets");
                }
            }
            case PILLAR3 -> {
                if (request.manualBalance() != null) {
                    throw new IllegalArgumentException("manualBalance must be empty for PILLAR3 wealth buckets");
                }
                requireNoAssetClasses(request);
                validateSinglePillar3Bucket(userId, excludeId);
            }
        }
    }

    private static void requireNoAssetClasses(WealthBucketRequest request) {
        if (request.assetClasses() != null && !request.assetClasses().isEmpty()) {
            throw new IllegalArgumentException(
                    "assetClasses must be empty for " + request.source() + " wealth buckets");
        }
    }

    /** A second PILLAR3 bucket would double-count the default-scenario balance in the overview. */
    private void validateSinglePillar3Bucket(String userId, UUID excludeId) {
        boolean pillar3BucketExists = excludeId == null
                ? bucketRepository.existsByUserIdAndSource(userId, WealthSource.PILLAR3)
                : bucketRepository.existsByUserIdAndSourceAndIdNot(userId, WealthSource.PILLAR3, excludeId);
        if (pillar3BucketExists) {
            throw new IllegalArgumentException("A PILLAR3 wealth bucket already exists"
                    + " — a second one would double-count the pillar 3a balance");
        }
    }

    /** Each asset class may feed at most one bucket per user; excludeId skips the bucket being updated. */
    private void validateAssetClassesNotLinkedElsewhere(WealthBucketRequest request, String userId, UUID excludeId) {
        List<AssetClass> requested = request.assetClasses();
        if (requested == null || requested.isEmpty()) {
            return;
        }
        bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .filter(bucket -> !bucket.getId().equals(excludeId))
                .forEach(bucket -> bucket.assetClassList().stream()
                        .filter(requested::contains)
                        .findFirst()
                        .ifPresent(assetClass -> {
                            throw new IllegalArgumentException("Asset class " + assetClass
                                    + " is already linked to wealth bucket '" + bucket.getName() + "'");
                        }));
    }

    private static WealthBucket toEntity(WealthBucketRequest request, String userId, WealthBucket existing) {
        WealthSource source = request.source();
        return WealthBucket.builder()
                .id(existing != null ? existing.getId() : null)
                .userId(userId)
                .name(request.name())
                .note(request.note())
                .source(source)
                .manualBalance(source == WealthSource.MANUAL ? request.manualBalance() : null)
                .assetClasses(source == WealthSource.PORTFOLIO
                        ? WealthBucket.toStorage(request.assetClasses()) : null)
                .monthlyRate(request.monthlyRate() != null ? request.monthlyRate() : BigDecimal.ZERO)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .createdAt(existing != null ? existing.getCreatedAt() : null)
                .build();
    }
}
