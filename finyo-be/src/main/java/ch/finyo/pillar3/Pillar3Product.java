package ch.finyo.pillar3;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Curated pillar 3a fund/strategy master data. Reference/catalog table without
 * user scope — maintained by admins via the import and CRUD endpoints.
 */
@Entity
@Table(name = "pillar3_product")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Pillar3Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 100, nullable = false)
    private String provider;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(length = 12, unique = true, nullable = false)
    private String isin;

    /** Swiss valor number; stored as string to preserve leading zeros. */
    @Column(length = 20)
    private String valor;

    @Column(name = "equity_pct", precision = 5, scale = 2, nullable = false)
    private BigDecimal equityPct;

    @Column(name = "ter_pct", precision = 5, scale = 3, nullable = false)
    private BigDecimal terPct;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
