package ch.finyoapi.cli;

import ch.finyoapi.savings.SavingsGoalRequest;
import ch.finyoapi.savings.SavingsGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@ShellComponent
@RequiredArgsConstructor
public class SavingsCommands {

    private final SavingsGoalService savingsGoalService;
    private final CliUserContext cliUserContext;

    @ShellMethod(key = "savings list", value = "List active savings goals with progress")
    public String list() {
        var goals = savingsGoalService.getAll(cliUserContext.getUserId());
        if (goals.isEmpty()) {
            return "No active savings goals.";
        }

        StringBuilder sb = new StringBuilder("Active Savings Goals\n");
        sb.append("─".repeat(75)).append("\n");
        sb.append(String.format("%-25s  %10s  %10s  %8s  %s%n",
                "Name", "Target CHF", "Current CHF", "Progress", "Target Date"));
        sb.append("─".repeat(75)).append("\n");
        goals.forEach(g -> {
            String targetDate = g.targetDate() != null ? g.targetDate().toString() : "-";
            String monthlyRequired = g.monthlyRequired() != null
                    ? String.format("  (CHF %.2f/mo needed)", g.monthlyRequired())
                    : "";
            sb.append(String.format("%-25s  %10.2f  %10.2f  %7.1f%%%s  %s%n",
                    truncate(g.name(), 25),
                    g.targetAmount(),
                    g.currentAmount(),
                    g.progressPercentage(),
                    monthlyRequired,
                    targetDate));
        });
        return sb.toString();
    }

    @ShellMethod(key = "savings add", value = "Create a new savings goal")
    public String add(
            @ShellOption(help = "Goal name")
            String name,
            @ShellOption(help = "Target amount in CHF")
            BigDecimal target,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Target date (yyyy-MM-dd)")
            String date
    ) {
        var request = new SavingsGoalRequest(
                name,
                target,
                BigDecimal.ZERO,
                date != null ? LocalDate.parse(date) : null,
                null,
                null,
                null
        );
        var result = savingsGoalService.create(request, cliUserContext.getUserId());
        return String.format("Created savings goal '%s' (id: %s) with target CHF %.2f",
                result.name(), result.id(), result.targetAmount());
    }

    @ShellMethod(key = "savings update", value = "Update the current amount of a savings goal")
    public String update(
            @ShellOption(help = "Savings goal UUID")
            String id,
            @ShellOption(help = "New current amount in CHF")
            BigDecimal current
    ) {
        UUID goalId = UUID.fromString(id);
        var existing = savingsGoalService.getById(goalId, cliUserContext.getUserId());
        var request = new SavingsGoalRequest(
                existing.name(),
                existing.targetAmount(),
                current,
                existing.targetDate(),
                existing.accountId(),
                existing.icon(),
                existing.color()
        );
        var result = savingsGoalService.update(goalId, request, cliUserContext.getUserId());
        return String.format("Updated savings goal '%s': CHF %.2f / %.2f (%.1f%%)",
                result.name(), result.currentAmount(), result.targetAmount(), result.progressPercentage());
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
