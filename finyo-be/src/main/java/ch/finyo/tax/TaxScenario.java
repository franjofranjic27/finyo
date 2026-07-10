package ch.finyo.tax;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Input snapshot of a tax year — each save creates a new row. The tax inputs
 * themselves are immutable; only the default flag (and thus updatedAt) is mutable.
 */
@Entity
@Table(name = "tax_scenario")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaxScenario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "tax_year_id", nullable = false)
    private UUID taxYearId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "canton_code", length = 2)
    private String cantonCode;

    @Column(name = "bfs_number")
    private Integer bfsNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "civil_status", length = 20)
    private TaxCivilStatus civilStatus;

    @Column(name = "number_of_children")
    private Integer numberOfChildren;

    @Enumerated(EnumType.STRING)
    @Column(name = "church_affiliation", length = 20)
    private ChurchAffiliation churchAffiliation;

    @Column(name = "gross_employment_income")
    private BigDecimal grossEmploymentIncome;

    @Column(name = "self_employment_income")
    private BigDecimal selfEmploymentIncome;

    @Column(name = "investment_income")
    private BigDecimal investmentIncome;

    @Column(name = "rental_income")
    private BigDecimal rentalIncome;

    @Column(name = "deduction_professional_expenses")
    private BigDecimal deductionProfessionalExpenses;

    @Column(name = "deduction_insurance_premiums")
    private BigDecimal deductionInsurancePremiums;

    @Column(name = "deduction_charitable_donations")
    private BigDecimal deductionCharitableDonations;

    @Column(name = "deduction_debt_interest")
    private BigDecimal deductionDebtInterest;

    @Column(name = "pillar3a_contribution")
    private BigDecimal pillar3aContribution;

    @Column(name = "net_wealth")
    private BigDecimal netWealth;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
