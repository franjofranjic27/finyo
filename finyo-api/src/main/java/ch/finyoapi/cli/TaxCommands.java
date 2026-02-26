package ch.finyoapi.cli;

import ch.finyoapi.tax.TaxCalculationService;
import ch.finyoapi.tax.TaxCivilStatus;
import ch.finyoapi.tax.TaxInputRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@ShellComponent
@RequiredArgsConstructor
public class TaxCommands {

    private final TaxCalculationService taxCalculationService;

    @ShellMethod(key = "tax calculate", value = "Calculate Swiss income tax estimate")
    public String calculate(
            @ShellOption(help = "Gross employment income in CHF")
            BigDecimal income,
            @ShellOption(defaultValue = "SG", help = "Canton code (e.g. SG, ZH, BE)")
            String canton,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Tax year (defaults to current year)")
            String year,
            @ShellOption(defaultValue = "SINGLE", help = "Civil status: SINGLE, MARRIED, SINGLE_PARENT")
            TaxCivilStatus status,
            @ShellOption(defaultValue = "0", help = "Pillar 3a contribution in CHF")
            BigDecimal pillar3a
    ) {
        int taxYear = year != null ? Integer.parseInt(year) : LocalDate.now().getYear();

        var request = new TaxInputRequest(
                taxYear,
                canton,
                null,
                status,
                0,
                income,
                null, null, null,
                null, null, null, null,
                pillar3a.compareTo(BigDecimal.ZERO) > 0 ? pillar3a : null,
                null
        );

        var result = taxCalculationService.calculate(request);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Tax Calculation %d — Canton %s — %s%n",
                result.taxYear(), result.cantonCode(), result.civilStatus()));
        sb.append("─".repeat(55)).append("\n");
        sb.append(String.format("  Gross Income:       CHF %,12.2f%n", result.grossIncome()));
        sb.append(String.format("  Total Deductions:   CHF %,12.2f%n", result.totalDeductions()));
        sb.append(String.format("  Taxable Income:     CHF %,12.2f%n", result.taxableIncome()));
        sb.append("─".repeat(55)).append("\n");
        sb.append(String.format("  Federal Tax:        CHF %,12.2f%n", result.federalTax()));
        sb.append(String.format("  Cantonal Tax:       CHF %,12.2f%n", result.cantonalTax()));
        sb.append(String.format("  Communal Tax:       CHF %,12.2f%n", result.communalTax()));
        sb.append("─".repeat(55)).append("\n");
        sb.append(String.format("  Total Income Tax:   CHF %,12.2f%n", result.totalIncomeTax()));
        if (result.wealthTax() != null && result.wealthTax().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("  Wealth Tax:         CHF %,12.2f%n", result.wealthTax()));
        }
        sb.append(String.format("  Grand Total:        CHF %,12.2f%n", result.grandTotal()));
        sb.append("─".repeat(55)).append("\n");
        sb.append(String.format("  Effective Rate:         %11.2f%%%n", result.effectiveRatePercent()));
        sb.append(String.format("  Marginal Rate:          %11.2f%%%n", result.marginalRatePercent()));
        if (result.pillar3aTaxSaving() != null && result.pillar3aTaxSaving().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("─".repeat(55)).append("\n");
            sb.append(String.format("  Pillar 3a Saving:   CHF %,12.2f%n", result.pillar3aTaxSaving()));
        }
        return sb.toString();
    }

    @ShellMethod(key = "tax pillar3", value = "Project Pillar 3a balance growth and tax saving")
    public String pillar3(
            @ShellOption(help = "Current Pillar 3a balance in CHF")
            BigDecimal balance,
            @ShellOption(help = "Annual contribution in CHF")
            BigDecimal contribution,
            @ShellOption(help = "Investment horizon in years")
            int years,
            @ShellOption(defaultValue = "5.0", help = "Assumed annual return in percent")
            double returnPercent,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Gross income for tax-saving estimate")
            BigDecimal income,
            @ShellOption(defaultValue = "SG", help = "Canton code for tax-saving estimate")
            String canton,
            @ShellOption(defaultValue = "SINGLE", help = "Civil status: SINGLE, MARRIED, SINGLE_PARENT")
            TaxCivilStatus status
    ) {
        int taxYear = LocalDate.now().getYear();

        // Compute projection using compound growth
        BigDecimal currentBalance = balance;
        BigDecimal rateDecimal = BigDecimal.valueOf(returnPercent / 100.0);
        BigDecimal totalContributed = BigDecimal.ZERO;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Pillar 3a Projection — %d years at %.1f%% p.a.%n", years, returnPercent));
        sb.append("─".repeat(60)).append("\n");
        sb.append(String.format("  Starting balance:   CHF %,12.2f%n", balance));
        sb.append(String.format("  Annual contribution: CHF %,11.2f%n", contribution));
        sb.append("─".repeat(60)).append("\n");
        sb.append(String.format("  %-4s  %15s  %15s  %15s%n", "Year", "Balance CHF", "Contributed CHF", "Returns CHF"));
        sb.append("─".repeat(60)).append("\n");

        BigDecimal startingBalance = balance;
        for (int y = 1; y <= years; y++) {
            BigDecimal growth = currentBalance.add(contribution)
                    .multiply(rateDecimal)
                    .setScale(2, RoundingMode.HALF_UP);
            currentBalance = currentBalance.add(contribution).add(growth);
            totalContributed = totalContributed.add(contribution);
            BigDecimal totalReturns = currentBalance.subtract(startingBalance).subtract(totalContributed);

            if (y <= 5 || y % 5 == 0 || y == years) {
                sb.append(String.format("  %-4d  %,15.2f  %,15.2f  %,15.2f%n",
                        y, currentBalance, totalContributed, totalReturns));
            }
        }

        sb.append("─".repeat(60)).append("\n");
        sb.append(String.format("  Final balance:      CHF %,12.2f%n", currentBalance));

        // Annual tax saving estimate
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            var reqWith = new TaxInputRequest(taxYear, canton, null, status, 0,
                    income, null, null, null, null, null, null, null, contribution, null);
            var reqWithout = new TaxInputRequest(taxYear, canton, null, status, 0,
                    income, null, null, null, null, null, null, null, null, null);
            var resultWith = taxCalculationService.calculate(reqWith);
            var resultWithout = taxCalculationService.calculate(reqWithout);
            BigDecimal annualSaving = resultWithout.grandTotal().subtract(resultWith.grandTotal())
                    .max(BigDecimal.ZERO);
            BigDecimal totalSaving = annualSaving.multiply(BigDecimal.valueOf(years));
            sb.append("─".repeat(60)).append("\n");
            sb.append(String.format("  Annual tax saving:  CHF %,12.2f%n", annualSaving));
            sb.append(String.format("  Total tax saving:   CHF %,12.2f (over %d years)%n",
                    totalSaving, years));
        }

        return sb.toString();
    }
}
