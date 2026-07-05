package ch.finyo.tax;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxCalculationService {

    // 2025 legal maximums for Pillar 3a contributions
    private static final BigDecimal PILLAR3A_MAX_EMPLOYEE     = new BigDecimal("7258");
    private static final BigDecimal PILLAR3A_MAX_SELF_EMPLOYED = new BigDecimal("36288");

    // Swiss standard professional-expense deduction bounds
    private static final BigDecimal PROFESSIONAL_EXPENSE_RATE = new BigDecimal("0.03");
    private static final BigDecimal PROFESSIONAL_EXPENSE_MIN  = new BigDecimal("2200");
    private static final BigDecimal PROFESSIONAL_EXPENSE_MAX  = new BigDecimal("4400");

    // Child deduction per child (federal / SG canton)
    private static final BigDecimal CHILD_DEDUCTION = new BigDecimal("6700");

    private final TaxFederalRateRepository federalRateRepository;
    private final TaxCantonRateRepository cantonRateRepository;
    private final TaxCommuneMultiplierRepository communeMultiplierRepository;

    public TaxResultResponse calculate(TaxInputRequest req) {
        log.debug("Calculating tax for year={}, canton={}, civilStatus={}",
                req.taxYear(), req.cantonCode(), req.civilStatus());

        // 1. Aggregate gross income
        BigDecimal grossIncome = sum(
                req.grossEmploymentIncome(),
                req.selfEmploymentIncome(),
                req.investmentIncome(),
                req.rentalIncome()
        );

        // 2. Calculate deductions
        BigDecimal professionalExpenses = calculateProfessionalExpenses(
                req.grossEmploymentIncome(), req.deductionProfessionalExpenses());

        BigDecimal pillar3aCap = req.selfEmploymentIncome() != null
                && req.selfEmploymentIncome().compareTo(BigDecimal.ZERO) > 0
                ? PILLAR3A_MAX_SELF_EMPLOYED : PILLAR3A_MAX_EMPLOYEE;
        BigDecimal pillar3a = req.pillar3aContribution() != null
                ? req.pillar3aContribution().min(pillar3aCap) : BigDecimal.ZERO;

        BigDecimal totalDeductions = sum(
                professionalExpenses,
                req.deductionInsurancePremiums(),
                req.deductionCharitableDonations(),
                req.deductionDebtInterest(),
                pillar3a
        );

        // Social deductions for children
        BigDecimal childDeduction = BigDecimal.valueOf(req.numberOfChildren()).multiply(CHILD_DEDUCTION);
        totalDeductions = totalDeductions.add(childDeduction);

        BigDecimal taxableIncome = grossIncome.subtract(totalDeductions).max(BigDecimal.ZERO);
        // Round down to nearest 100 CHF (Swiss practice)
        taxableIncome = roundToHundred(taxableIncome);

        // 3. Determine tariff
        String tariff = determineTariff(req.civilStatus());

        // 4. Calculate federal tax (Direkte Bundessteuer)
        List<TaxFederalRate> federalBrackets =
                federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(req.taxYear(), tariff);
        BigDecimal federalTax = calculateTaxFromFederalBrackets(taxableIncome, federalBrackets);

        // 5. Calculate cantonal simple tax (einfache Steuer)
        List<TaxCantonRate> cantonBrackets =
                cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(
                        req.taxYear(), req.cantonCode().toUpperCase(), tariff);
        BigDecimal cantonalSimpleTax = calculateTaxFromCantonBrackets(taxableIncome, cantonBrackets);

        // 6. Cantonal tax — for SG, Staatssteuer equals the simple tax (multiplier 1.0)
        BigDecimal cantonalTax = cantonalSimpleTax;

        // 7. Communal and church tax
        BigDecimal communalTax = BigDecimal.ZERO;
        BigDecimal churchTax = BigDecimal.ZERO;
        String communeName = req.cantonCode().toUpperCase();
        if (req.bfsNumber() != null) {
            var commune = communeMultiplierRepository
                    .findByTaxYearAndBfsNumber(req.taxYear(), req.bfsNumber());
            if (commune.isPresent()) {
                communeName = commune.get().getCommuneName();
                communalTax = cantonalSimpleTax
                        .multiply(commune.get().getMultiplier())
                        .setScale(2, RoundingMode.HALF_UP);
                churchTax = cantonalSimpleTax
                        .multiply(churchMultiplier(commune.get(), req.churchAffiliation()))
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        BigDecimal totalIncomeTax = federalTax.add(cantonalTax).add(communalTax).add(churchTax);

        // 8. Effective and marginal rates
        double effectiveRate = grossIncome.compareTo(BigDecimal.ZERO) == 0 ? 0.0
                : totalIncomeTax.divide(grossIncome, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();

        double marginalRate = calculateMarginalRate(
                taxableIncome, req.taxYear(), tariff, req.cantonCode(), req.bfsNumber(),
                req.churchAffiliation());

        // 9. Wealth tax (simplified cantonal — SG rate approx 0.3‰ above 100 000 CHF)
        BigDecimal wealthTax = calculateWealthTax(req.netWealth(), req.cantonCode());

        // 10. Pillar 3a tax-saving calculation
        BigDecimal taxSavingWith3a = BigDecimal.ZERO;
        if (pillar3a.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal taxableWithout3a = roundToHundred(taxableIncome.add(pillar3a));

            BigDecimal federalWithout = calculateTaxFromFederalBrackets(taxableWithout3a,
                    federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(req.taxYear(), tariff));
            BigDecimal cantonalWithout = calculateTaxFromCantonBrackets(taxableWithout3a,
                    cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(
                            req.taxYear(), req.cantonCode().toUpperCase(), tariff));

            BigDecimal communalWithout = BigDecimal.ZERO;
            BigDecimal churchWithout = BigDecimal.ZERO;
            if (req.bfsNumber() != null) {
                var commune = communeMultiplierRepository
                        .findByTaxYearAndBfsNumber(req.taxYear(), req.bfsNumber());
                if (commune.isPresent()) {
                    communalWithout = cantonalWithout
                            .multiply(commune.get().getMultiplier())
                            .setScale(2, RoundingMode.HALF_UP);
                    churchWithout = cantonalWithout
                            .multiply(churchMultiplier(commune.get(), req.churchAffiliation()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }

            BigDecimal totalWithout = federalWithout.add(cantonalWithout)
                    .add(communalWithout).add(churchWithout);
            taxSavingWith3a = totalWithout.subtract(totalIncomeTax).max(BigDecimal.ZERO);
        }

        BigDecimal grandTotal = totalIncomeTax.add(wealthTax);

        // 11. Build breakdown list for chart data
        List<TaxBreakdownItem> breakdown = new ArrayList<>();
        breakdown.add(new TaxBreakdownItem("Federal Tax (DBSt)", federalTax,
                pct(federalTax, grandTotal)));
        breakdown.add(new TaxBreakdownItem("Cantonal Tax", cantonalTax,
                pct(cantonalTax, grandTotal)));
        breakdown.add(new TaxBreakdownItem("Communal Tax", communalTax,
                pct(communalTax, grandTotal)));
        if (churchTax.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.add(new TaxBreakdownItem("Church Tax", churchTax,
                    pct(churchTax, grandTotal)));
        }
        if (wealthTax.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.add(new TaxBreakdownItem("Wealth Tax", wealthTax,
                    pct(wealthTax, grandTotal)));
        }

        return new TaxResultResponse(
                req.taxYear(), req.cantonCode().toUpperCase(), communeName, req.civilStatus(),
                grossIncome, totalDeductions, taxableIncome,
                federalTax, cantonalTax, communalTax, churchTax, totalIncomeTax,
                effectiveRate, marginalRate,
                wealthTax, grandTotal,
                taxSavingWith3a, breakdown
        );
    }

    public List<TaxCommuneMultiplier> getCommunes(int taxYear, String cantonCode) {
        return communeMultiplierRepository
                .findByTaxYearAndCantonCodeOrderByCommuneNameAsc(taxYear, cantonCode.toUpperCase());
    }

    // -------------------------------------------------------------------------
    // Bracket calculation — typed overloads to avoid reflection
    // -------------------------------------------------------------------------

    private BigDecimal calculateTaxFromFederalBrackets(BigDecimal taxableIncome,
                                                        List<TaxFederalRate> brackets) {
        if (brackets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (int i = brackets.size() - 1; i >= 0; i--) {
            TaxFederalRate bracket = brackets.get(i);
            if (taxableIncome.compareTo(bracket.getIncomeFrom()) >= 0
                    && (bracket.getIncomeTo() == null
                        || taxableIncome.compareTo(bracket.getIncomeTo()) <= 0)) {
                BigDecimal excess = taxableIncome.subtract(bracket.getIncomeFrom());
                BigDecimal variable = excess
                        .multiply(bracket.getRatePerHundred())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                return bracket.getBaseTax().add(variable).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateTaxFromCantonBrackets(BigDecimal taxableIncome,
                                                       List<TaxCantonRate> brackets) {
        if (brackets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        for (int i = brackets.size() - 1; i >= 0; i--) {
            TaxCantonRate bracket = brackets.get(i);
            if (taxableIncome.compareTo(bracket.getIncomeFrom()) >= 0
                    && (bracket.getIncomeTo() == null
                        || taxableIncome.compareTo(bracket.getIncomeTo()) <= 0)) {
                BigDecimal excess = taxableIncome.subtract(bracket.getIncomeFrom());
                BigDecimal variable = excess
                        .multiply(bracket.getRatePerHundred())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                return bracket.getBaseTax().add(variable).setScale(2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    // -------------------------------------------------------------------------
    // Marginal rate — incremental tax on 1 000 CHF additional income
    // -------------------------------------------------------------------------

    private double calculateMarginalRate(BigDecimal taxableIncome, int taxYear, String tariff,
                                          String cantonCode, Integer bfsNumber,
                                          ChurchAffiliation churchAffiliation) {
        BigDecimal increment = new BigDecimal("1000");
        BigDecimal higher = taxableIncome.add(increment);

        List<TaxFederalRate> federalBrackets =
                federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(taxYear, tariff);
        List<TaxCantonRate> cantonBrackets =
                cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(
                        taxYear, cantonCode.toUpperCase(), tariff);

        BigDecimal taxCurrent = calculateTaxFromFederalBrackets(taxableIncome, federalBrackets)
                .add(calculateTaxFromCantonBrackets(taxableIncome, cantonBrackets));
        BigDecimal taxHigher = calculateTaxFromFederalBrackets(higher, federalBrackets)
                .add(calculateTaxFromCantonBrackets(higher, cantonBrackets));

        // Include commune and church multipliers in marginal rate if a commune is selected
        if (bfsNumber != null) {
            var commune = communeMultiplierRepository
                    .findByTaxYearAndBfsNumber(taxYear, bfsNumber);
            if (commune.isPresent()) {
                BigDecimal multiplier = commune.get().getMultiplier()
                        .add(churchMultiplier(commune.get(), churchAffiliation));
                BigDecimal cantonCurrent = calculateTaxFromCantonBrackets(taxableIncome, cantonBrackets);
                BigDecimal cantonHigher  = calculateTaxFromCantonBrackets(higher, cantonBrackets);
                taxCurrent = taxCurrent.add(cantonCurrent.multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
                taxHigher  = taxHigher.add(cantonHigher.multiply(multiplier).setScale(2, RoundingMode.HALF_UP));
            }
        }

        return taxHigher.subtract(taxCurrent)
                .divide(increment, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private BigDecimal churchMultiplier(TaxCommuneMultiplier commune, ChurchAffiliation affiliation) {
        if (affiliation == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal multiplier = switch (affiliation) {
            case NONE -> null;
            case PROTESTANT -> commune.getChurchMultiplierProtestant();
            case ROMAN_CATHOLIC -> commune.getChurchMultiplierRomanCatholic();
        };
        return multiplier != null ? multiplier : BigDecimal.ZERO;
    }

    private BigDecimal calculateProfessionalExpenses(BigDecimal employmentIncome,
                                                      BigDecimal userProvided) {
        if (userProvided != null && userProvided.compareTo(BigDecimal.ZERO) > 0) {
            return userProvided;
        }
        if (employmentIncome == null || employmentIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        // Swiss standard deduction: 3% of employment income, min 2 200 CHF, max 4 400 CHF
        BigDecimal flat = employmentIncome.multiply(PROFESSIONAL_EXPENSE_RATE);
        return flat.max(PROFESSIONAL_EXPENSE_MIN).min(PROFESSIONAL_EXPENSE_MAX);
    }

    private BigDecimal calculateWealthTax(BigDecimal netWealth, String cantonCode) {
        if (netWealth == null || netWealth.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        // Simplified SG wealth tax: 0.3‰ on wealth above 100 000 CHF.
        // Actual rates are progressive and canton-specific; a proper implementation
        // would query a wealth-tax bracket table similar to income tax.
        BigDecimal exempt = new BigDecimal("100000");
        BigDecimal taxableWealth = netWealth.subtract(exempt).max(BigDecimal.ZERO);
        return taxableWealth.multiply(new BigDecimal("0.0003"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String determineTariff(TaxCivilStatus status) {
        return switch (status) {
            case MARRIED, SINGLE_PARENT -> "B";
            case SINGLE -> "A";
        };
    }

    /** Rounds down to the nearest 100 CHF as required by Swiss tax law. */
    private BigDecimal roundToHundred(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .multiply(BigDecimal.valueOf(100));
    }

    /** Null-safe sum of an arbitrary number of BigDecimal values. */
    private BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }

    /** Returns the percentage of {@code part} relative to {@code total}, or 0.0 when total is zero. */
    private Double pct(BigDecimal part, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}
