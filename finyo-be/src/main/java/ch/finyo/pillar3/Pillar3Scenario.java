package ch.finyo.pillar3;

import ch.finyo.tax.TaxCivilStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Input snapshot of a pillar 3a projection — each save creates a new row. The
 * inputs themselves are immutable; only the default flag (and thus updatedAt)
 * is mutable. The linked product is referenced by id only: when it is deleted
 * the FK sets productId to NULL and the stored return percent takes over.
 */
@Entity
@Table(name = "pillar3_scenario")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Pillar3Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "current_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal currentBalance;

    @Column(name = "annual_contribution", precision = 19, scale = 4, nullable = false)
    private BigDecimal annualContribution;

    @Column(name = "assumed_annual_return_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal assumedAnnualReturnPercent;

    @Column(name = "years_to_retirement", nullable = false)
    private int yearsToRetirement;

    @Column(name = "gross_employment_income", precision = 19, scale = 4)
    private BigDecimal grossEmploymentIncome;

    @Enumerated(EnumType.STRING)
    @Column(name = "civil_status", length = 20)
    private TaxCivilStatus civilStatus;

    @Column(name = "canton_code", length = 2)
    private String cantonCode;

    @Column(name = "tax_year")
    private Integer taxYear;

    @Column(name = "product_id")
    private UUID productId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
