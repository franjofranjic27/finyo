package ch.finyoapi.cli;

import ch.finyoapi.budget.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
@RequiredArgsConstructor
public class BudgetCommands {

    private final BudgetService budgetService;
    private final CliUserContext cliUserContext;

    @ShellMethod(key = "budget list", value = "List all budgets")
    public String list() {
        var budgets = budgetService.getAll(cliUserContext.getUserId());
        if (budgets.isEmpty()) {
            return "No budgets configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-25s  %10s  %s%n", "Category", "Budget CHF", "Period"));
        sb.append("─".repeat(50)).append("\n");
        budgets.forEach(b -> sb.append(String.format("%-25s  %10s  %s%n",
                b.categoryName(), b.amount().toPlainString(), b.period())));
        return sb.toString();
    }

    @ShellMethod(key = "budget status", value = "Show current month budget status")
    public String status() {
        var statusList = budgetService.getCurrentStatus(cliUserContext.getUserId());
        if (statusList.isEmpty()) {
            return "No active budgets.";
        }

        StringBuilder sb = new StringBuilder("Current Month Budget Status\n");
        sb.append("─".repeat(65)).append("\n");
        sb.append(String.format("%-22s %9s %9s %9s %7s%n", "Category", "Budget", "Spent", "Remaining", "Used%"));
        sb.append("─".repeat(65)).append("\n");
        statusList.forEach(s -> {
            String flag = s.overBudget() ? " !" : s.percentageUsed() > 80 ? " ~" : "";
            sb.append(String.format("%-22s %9.2f %9.2f %9.2f %6.1f%%%s%n",
                    s.categoryName(),
                    s.budgetAmount(),
                    s.spent(),
                    s.remaining(),
                    s.percentageUsed(),
                    flag));
        });
        return sb.toString();
    }
}
