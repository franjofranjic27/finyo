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
 * the invariant that each asset class is linked to at most one bucket per user
 * (a class linked twice would double-count its portfolio value in the overview).
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
        validateSourceFields(request);
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
        validateSourceFields(request);
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

    private static void validateSourceFields(WealthBucketRequest request) {
        if (request.source() == WealthSource.MANUAL) {
            if (request.manualBalance() == null) {
                throw new IllegalArgumentException("manualBalance is required for MANUAL wealth buckets");
            }
            if (request.assetClasses() != null && !request.assetClasses().isEmpty()) {
                throw new IllegalArgumentException("assetClasses must be empty for MANUAL wealth buckets");
            }
        } else if (request.assetClasses() == null || request.assetClasses().isEmpty()) {
            throw new IllegalArgumentException("assetClasses must not be empty for PORTFOLIO wealth buckets");
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
        boolean manual = request.source() == WealthSource.MANUAL;
        return WealthBucket.builder()
                .id(existing != null ? existing.getId() : null)
                .userId(userId)
                .name(request.name())
                .note(request.note())
                .source(request.source())
                .manualBalance(manual ? request.manualBalance() : null)
                .assetClasses(manual ? null : WealthBucket.toStorage(request.assetClasses()))
                .monthlyRate(request.monthlyRate() != null ? request.monthlyRate() : BigDecimal.ZERO)
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .createdAt(existing != null ? existing.getCreatedAt() : null)
                .build();
    }
}
