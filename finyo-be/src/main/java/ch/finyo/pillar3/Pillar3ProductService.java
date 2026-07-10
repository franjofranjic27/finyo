package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Pillar3ProductService {

    private static final String RESOURCE_NAME = "Pillar3Product";

    private final Pillar3ProductRepository productRepository;
    private final Validator validator;

    @Transactional(readOnly = true)
    public List<Pillar3ProductResponse> getActive(String search) {
        log.debug("Fetching active pillar3 products search='{}'", search);
        List<Pillar3Product> products = (search == null || search.isBlank())
                ? productRepository.findByActiveTrueOrderBySortOrderAscNameAsc()
                : productRepository.searchActive(search.trim());
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<Pillar3ProductResponse> getAll() {
        log.debug("Fetching all pillar3 products (including inactive)");
        return productRepository.findAllByOrderBySortOrderAscNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public Pillar3ProductResponse create(Pillar3ProductRequest request) {
        String isin = normalizeIsin(request.isin());
        log.info("Creating pillar3 product isin={}", isin);
        productRepository.findByIsin(isin).ifPresent(existing -> {
            throw new IllegalStateException("Pillar3Product with ISIN " + isin + " already exists");
        });

        Pillar3Product saved = productRepository.save(Pillar3Product.builder()
                .provider(request.provider())
                .name(request.name())
                .isin(isin)
                .valor(request.valor())
                .equityPct(request.equityPct())
                .terPct(request.terPct())
                .active(request.active() == null || request.active())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build());
        log.info("Created pillar3 product id={} isin={}", saved.getId(), saved.getIsin());
        return toResponse(saved);
    }

    @Transactional
    public Pillar3ProductResponse update(UUID id, Pillar3ProductRequest request) {
        log.info("Updating pillar3 product id={}", id);
        Pillar3Product existing = productRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        String isin = normalizeIsin(request.isin());
        productRepository.findByIsin(isin)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalStateException("Pillar3Product with ISIN " + isin + " already exists");
                });

        Pillar3Product saved = productRepository.save(applyRequest(existing, request, isin));
        log.info("Updated pillar3 product id={} isin={}", saved.getId(), saved.getIsin());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting pillar3 product id={}", id);
        // Hard delete: nothing references this catalog table yet.
        if (!productRepository.existsById(id)) {
            throw ResourceNotFoundException.of(RESOURCE_NAME, id);
        }
        productRepository.deleteById(id);
        log.info("Deleted pillar3 product id={}", id);
    }

    /**
     * Bulk upsert by ISIN. Deliberately not batch-transactional: each row is
     * validated and persisted on its own so one bad row never aborts the batch.
     * Duplicate ISINs within the same payload resolve last-wins (the earlier
     * row is persisted first, later ones update it). Idempotent: re-importing
     * the same payload yields {@code created=0, updated=n}.
     */
    public Pillar3ProductImportResult importProducts(Pillar3ProductImportRequest request) {
        List<Pillar3ProductRequest> rows = request.products();
        log.info("Importing {} pillar3 product rows", rows.size());

        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Pillar3ProductRequest row = rows.get(i);
            try {
                validateRow(row);
                if (upsert(row)) {
                    created++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                failed++;
                errors.add("row " + (i + 1) + " (ISIN " + row.isin() + "): " + e.getMessage());
            }
        }

        log.info("Pillar3 product import finished: total={} created={} updated={} failed={}",
                rows.size(), created, updated, failed);
        return new Pillar3ProductImportResult(rows.size(), created, updated, failed, errors);
    }

    /** @return {@code true} if the row created a new product, {@code false} if it updated an existing one. */
    private boolean upsert(Pillar3ProductRequest row) {
        String isin = normalizeIsin(row.isin());
        Optional<Pillar3Product> existing = productRepository.findByIsin(isin);
        if (existing.isPresent()) {
            productRepository.save(applyRequest(existing.get(), row, isin));
            return false;
        }
        productRepository.save(Pillar3Product.builder()
                .provider(row.provider())
                .name(row.name())
                .isin(isin)
                .valor(row.valor())
                .equityPct(row.equityPct())
                .terPct(row.terPct())
                .active(row.active() == null || row.active())
                .sortOrder(row.sortOrder() != null ? row.sortOrder() : 0)
                .build());
        return true;
    }

    private void validateRow(Pillar3ProductRequest row) {
        Set<ConstraintViolation<Pillar3ProductRequest>> violations = validator.validate(row);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(detail);
        }
    }

    /** Copies request fields onto an existing product; {@code active}/{@code sortOrder} only when provided. */
    private Pillar3Product applyRequest(Pillar3Product existing, Pillar3ProductRequest request, String normalizedIsin) {
        return existing.toBuilder()
                .provider(request.provider())
                .name(request.name())
                .isin(normalizedIsin)
                .valor(request.valor())
                .equityPct(request.equityPct())
                .terPct(request.terPct())
                .active(request.active() != null ? request.active() : existing.isActive())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : existing.getSortOrder())
                .build();
    }

    /** Must be applied on every write path before {@code findByIsin}, otherwise case variants would bypass the UNIQUE constraint. */
    private String normalizeIsin(String isin) {
        return isin.trim().toUpperCase(Locale.ROOT);
    }

    private Pillar3ProductResponse toResponse(Pillar3Product product) {
        return new Pillar3ProductResponse(
                product.getId(),
                product.getProvider(),
                product.getName(),
                product.getIsin(),
                product.getValor(),
                product.getEquityPct(),
                product.getTerPct(),
                product.isActive(),
                product.getSortOrder(),
                Pillar3ReturnModel.grossReturnPct(product.getEquityPct()),
                Pillar3ReturnModel.netReturnPct(product.getEquityPct(), product.getTerPct())
        );
    }
}
