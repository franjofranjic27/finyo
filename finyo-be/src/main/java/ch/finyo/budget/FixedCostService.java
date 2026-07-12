package ch.finyo.budget;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedCostService {

    private static final String RESOURCE_NAME = "FixedCost";

    private static final Comparator<FixedCostResponse> BY_YEARLY_AMOUNT_DESC_THEN_NAME =
            Comparator.comparing(FixedCostResponse::amountPerYear, Comparator.reverseOrder())
                    .thenComparing(FixedCostResponse::name);

    private final FixedCostRepository fixedCostRepository;

    @Transactional(readOnly = true)
    public FixedCostListResponse getAll(String userId) {
        log.debug("Fetching fixed costs for user={}", userId);
        return FixedCostListResponse.of(mapSorted(fixedCostRepository.findByUserId(userId)));
    }

    /** Monthly total across all fixed costs of the user (2 dp, HALF_UP per item). */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPerMonth(String userId) {
        return FixedCostListResponse.of(mapSorted(fixedCostRepository.findByUserId(userId))).totalPerMonth();
    }

    @Transactional
    public FixedCostResponse create(FixedCostRequest request, String userId) {
        log.info("Creating fixed cost name='{}' for user={}", request.name(), userId);
        FixedCost saved = fixedCostRepository.save(newFixedCost(request, userId));
        log.info("Created fixed cost id={} for user={}", saved.getId(), userId);
        return FixedCostResponse.from(saved);
    }

    @Transactional
    public FixedCostResponse update(UUID id, FixedCostRequest request, String userId) {
        log.info("Updating fixed cost id={} for user={}", id, userId);
        var existing = fixedCostRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        FixedCost saved = fixedCostRepository.save(applyRequest(existing, request));
        log.info("Updated fixed cost id={} for user={}", saved.getId(), userId);
        return FixedCostResponse.from(saved);
    }

    /**
     * Bulk upsert: each item is matched against the user's existing fixed costs by
     * normalized name (trimmed, lowercased). A match updates that row — keeping the
     * request's casing for the name — otherwise a new row is inserted. Items later
     * in the batch that normalize to the same name update the row an earlier item
     * just wrote (last one wins). A failing row is reported in the result without
     * aborting the batch.
     */
    @Transactional
    public FixedCostBulkResult bulkUpsert(FixedCostBulkRequest request, String userId) {
        List<FixedCostRequest> items = request.items();
        log.info("Bulk importing {} fixed cost rows for user={}", items.size(), userId);

        Map<String, FixedCost> byNormalizedName = loadExistingByNormalizedName(userId);

        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            FixedCostRequest item = items.get(i);
            try {
                String normalizedName = normalizeName(item.name());
                FixedCost existing = byNormalizedName.get(normalizedName);
                FixedCost saved = fixedCostRepository.save(existing != null
                        ? applyRequest(existing, item)
                        : newFixedCost(item, userId));
                if (existing != null) {
                    updated++;
                } else {
                    created++;
                }
                byNormalizedName.put(normalizedName, saved);
                log.debug("Bulk row {}: {} fixed cost name='{}' for user={}",
                        i + 1, existing != null ? "updated" : "created", item.name(), userId);
            } catch (Exception e) {
                // Unexpected (e.g. persistence) failures must not echo internals to the client.
                failed++;
                errors.add("row " + (i + 1) + ": persistence error");
                log.error("Bulk fixed cost import row {} failed for user={}", i + 1, userId, e);
            }
        }

        log.info("Bulk fixed cost import finished for user={}: created={} updated={} failed={}",
                userId, created, updated, failed);
        return new FixedCostBulkResult(created, updated, failed, List.copyOf(errors));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting fixed cost id={} for user={}", id, userId);
        fixedCostRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        fixedCostRepository.deleteById(id);
        log.info("Deleted fixed cost id={} for user={}", id, userId);
    }

    /**
     * Maps the user's fixed costs by normalized name. When several existing rows
     * normalize to the same name, the oldest one (by created_at) wins as the
     * upsert target; the others are left untouched.
     */
    private Map<String, FixedCost> loadExistingByNormalizedName(String userId) {
        Map<String, FixedCost> byNormalizedName = new HashMap<>();
        fixedCostRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(FixedCost::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(cost -> byNormalizedName.putIfAbsent(normalizeName(cost.getName()), cost));
        return byNormalizedName;
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static FixedCost newFixedCost(FixedCostRequest request, String userId) {
        return FixedCost.builder()
                .userId(userId)
                .name(request.name())
                .category(request.category())
                .paymentInterval(request.paymentInterval())
                .amount(request.amount())
                .build();
    }

    private static FixedCost applyRequest(FixedCost existing, FixedCostRequest request) {
        return FixedCost.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(request.name())
                .category(request.category())
                .paymentInterval(request.paymentInterval())
                .amount(request.amount())
                .build();
    }

    private List<FixedCostResponse> mapSorted(List<FixedCost> fixedCosts) {
        return fixedCosts.stream()
                .map(FixedCostResponse::from)
                .sorted(BY_YEARLY_AMOUNT_DESC_THEN_NAME)
                .toList();
    }
}
