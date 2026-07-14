package ch.finyo.tax;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Tax calculation inputs shared by {@link TaxYear} (the live, editable year)
 * and {@link TaxScenario} (a saved, editable scenario of those inputs). Both
 * tables use the same column names for these fields.
 */
@MappedSuperclass
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public abstract class TaxInputs {

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
}
