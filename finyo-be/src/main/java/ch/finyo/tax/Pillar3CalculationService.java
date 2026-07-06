package ch.finyo.tax;

import ch.finyo.common.SwissTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Pillar3CalculationService {

    // 2025 legal maximum for employees with 2nd pillar
    private static final BigDecimal PILLAR3A_MAX_EMPLOYEE = new BigDecimal("7258");

    // Approximate preferential flat payout-tax rate (canton + commune; varies 2–5%)
    private static final BigDecimal PAYOUT_TAX_RATE = new BigDecimal("0.035");

    // Conservative income-tax rate assumption for "equivalent taxable savings" comparison
    private static final BigDecimal ASSUMED_INCOME_TAX_ON_CONTRIBUTION = new BigDecimal("0.25");
    private static final BigDecimal ASSUMED_TAX_ON_RETURNS              = new BigDecimal("0.35");

    private final TaxCalculationService taxCalculationService;

    public Pillar3ResultResponse calculate(Pillar3InputRequest req) {
        log.debug("Calculating Pillar 3a projection: years={}, return={}%",
                req.yearsToRetirement(), req.assumedAnnualReturnPercent());

        BigDecimal contribution = req.annualContribution();
        BigDecimal maxContrib   = PILLAR3A_MAX_EMPLOYEE;
        boolean isMax           = contribution.compareTo(maxContrib) >= 0;
        BigDecimal remaining    = maxContrib.subtract(contribution).max(BigDecimal.ZERO);

        // Annual tax saving from current and maximum contributions
        BigDecimal annualTaxSaving  = BigDecimal.ZERO;
        BigDecimal taxSavingIfMax   = BigDecimal.ZERO;
        if (req.grossEmploymentIncome() != null && req.cantonCode() != null) {
            annualTaxSaving = calculateTaxSaving(req, contribution);
            taxSavingIfMax  = isMax ? annualTaxSaving : calculateTaxSaving(req, maxContrib);
        }

        // Future value: compound growth with end-of-year contribution
        BigDecimal rate          = BigDecimal.valueOf(req.assumedAnnualReturnPercent() / 100.0);
        BigDecimal balance       = req.currentBalance();
        BigDecimal totalContrib  = BigDecimal.ZERO;
        int currentYear          = Year.now(SwissTime.ZONE).getValue();

        List<Pillar3YearlyProjection> projection = new ArrayList<>(req.yearsToRetirement());

        for (int i = 1; i <= req.yearsToRetirement(); i++) {
            balance = balance.multiply(BigDecimal.ONE.add(rate))
                    .setScale(2, RoundingMode.HALF_UP);
            balance = balance.add(contribution);
            totalContrib = totalContrib.add(contribution);
            BigDecimal totalReturns = balance
                    .subtract(req.currentBalance())
                    .subtract(totalContrib);
            projection.add(new Pillar3YearlyProjection(
                    currentYear + i, balance, totalContrib, totalReturns));
        }

        BigDecimal projectedBalance = balance;
        BigDecimal totalReturnsAtRetirement = projectedBalance
                .subtract(req.currentBalance())
                .subtract(totalContrib);

        BigDecimal payoutTax = projectedBalance
                .multiply(PAYOUT_TAX_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal netValue = projectedBalance.subtract(payoutTax);

        // Equivalent taxable savings — same gross contributions but:
        //   - contributions are taxed at ~25% income tax before investing
        //   - annual returns are taxed at ~35% each year
        BigDecimal afterTaxRate         = rate.multiply(BigDecimal.ONE.subtract(ASSUMED_TAX_ON_RETURNS));
        BigDecimal afterTaxContribution = contribution.multiply(
                BigDecimal.ONE.subtract(ASSUMED_INCOME_TAX_ON_CONTRIBUTION));
        BigDecimal taxableBalance = req.currentBalance();
        for (int i = 1; i <= req.yearsToRetirement(); i++) {
            taxableBalance = taxableBalance.multiply(BigDecimal.ONE.add(afterTaxRate))
                    .setScale(2, RoundingMode.HALF_UP);
            taxableBalance = taxableBalance.add(afterTaxContribution);
        }

        return new Pillar3ResultResponse(
                contribution, maxContrib, isMax, remaining,
                annualTaxSaving, taxSavingIfMax,
                projectedBalance, totalContrib, totalReturnsAtRetirement,
                payoutTax, netValue, taxableBalance,
                projection
        );
    }

    // -------------------------------------------------------------------------
    // Tax-saving calculation via TaxCalculationService
    // -------------------------------------------------------------------------

    private BigDecimal calculateTaxSaving(Pillar3InputRequest req, BigDecimal contribution) {
        try {
            int year         = req.taxYear() > 0 ? req.taxYear() : Year.now(SwissTime.ZONE).getValue();
            String canton    = req.cantonCode() != null ? req.cantonCode() : "SG";
            TaxCivilStatus cs = req.civilStatus() != null ? req.civilStatus() : TaxCivilStatus.SINGLE;

            var inputWithout = new TaxInputRequest(
                    year, canton, null, cs,
                    0, null, req.grossEmploymentIncome(),
                    null, null, null,
                    null, null, null, null,
                    BigDecimal.ZERO, null
            );
            var inputWith = new TaxInputRequest(
                    year, canton, null, cs,
                    0, null, req.grossEmploymentIncome(),
                    null, null, null,
                    null, null, null, null,
                    contribution, null
            );

            BigDecimal taxWithout = taxCalculationService.calculate(inputWithout).totalIncomeTax();
            BigDecimal taxWith    = taxCalculationService.calculate(inputWith).totalIncomeTax();

            return taxWithout.subtract(taxWith).max(BigDecimal.ZERO);
        } catch (Exception e) {
            log.warn("Could not calculate Pillar 3a tax saving; defaulting to zero", e);
            return BigDecimal.ZERO;
        }
    }
}
