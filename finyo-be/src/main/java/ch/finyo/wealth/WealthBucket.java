package ch.finyo.wealth;

import ch.finyo.investment.AssetClass;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A manually maintained slice of the user's total net worth. Since the
 * auto-mirrored overview rows (portfolio, pillar 3a) are synthesized live and
 * never persisted, only MANUAL rows exist in this table (enforced by a CHECK
 * constraint since V38); the source and asset_classes columns remain for the
 * historical schema.
 */
@Entity
@Table(name = "wealth_bucket")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WealthBucket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 200)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private WealthSource source;

    @Column(name = "manual_balance", precision = 19, scale = 4)
    private BigDecimal manualBalance;

    @Column(name = "asset_classes", length = 100)
    private String assetClasses;

    @Column(name = "monthly_rate", precision = 19, scale = 4, nullable = false)
    private BigDecimal monthlyRate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Null-safe view of the stored comma-separated asset classes. */
    public List<AssetClass> assetClassList() {
        if (assetClasses == null || assetClasses.isBlank()) {
            return List.of();
        }
        return Arrays.stream(assetClasses.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(AssetClass::valueOf)
                .toList();
    }

    /** Converts a DTO list to the storage format; null for empty (MANUAL buckets). */
    public static String toStorage(List<AssetClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        return classes.stream()
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }
}
