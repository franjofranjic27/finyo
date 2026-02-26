package ch.finyoapi.cli;

import ch.finyoapi.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
@RequiredArgsConstructor
public class ProfileCommands {

    private final CliUserContext cliUserContext;
    private final AccountService accountService;

    @ShellMethod(key = "profile show", value = "Show current CLI user profile information")
    public String show() {
        String userId = cliUserContext.getUserId();
        return String.format(
                "CLI Profile%n" +
                "─".repeat(40) + "%n" +
                "  User ID : %s%n" +
                "─".repeat(40) + "%n" +
                "  Note: Authentication is currently based on the CLI_USER_ID%n" +
                "        environment variable. A future iteration will replace%n" +
                "        this with an OAuth2 client-credentials flow against%n" +
                "        Keycloak, resolving the user via the JWT subject claim.%n",
                userId
        );
    }

    @ShellMethod(key = "accounts list", value = "List all accounts for the current CLI user")
    public String accountsList() {
        var accounts = accountService.getAll(cliUserContext.getUserId());
        if (accounts.isEmpty()) {
            return "No accounts found.";
        }

        StringBuilder sb = new StringBuilder("Accounts\n");
        sb.append("─".repeat(70)).append("\n");
        sb.append(String.format("%-25s  %-15s  %-8s  %s%n", "Name", "Type", "Currency", "Initial Balance CHF"));
        sb.append("─".repeat(70)).append("\n");
        accounts.forEach(a -> sb.append(String.format("%-25s  %-15s  %-8s  %,.2f%n",
                truncate(a.name(), 25),
                a.type(),
                a.currency(),
                a.initialBalance())));
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
