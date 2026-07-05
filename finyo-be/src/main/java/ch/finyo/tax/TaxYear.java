package ch.finyo.tax;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_year")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaxYear {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TaxYearStatus status;

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

    @Column(name = "filing_deadline")
    private LocalDate filingDeadline;

    @Column(name = "filed_at")
    private LocalDate filedAt;

    @Column(name = "assessed_at")
    private LocalDate assessedAt;

    @Column(name = "assessed_amount")
    private BigDecimal assessedAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
