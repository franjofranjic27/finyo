package ch.finyoapi.cli;

import ch.finyoapi.transaction.TransactionRequest;
import ch.finyoapi.transaction.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@ShellComponent
@RequiredArgsConstructor
public class TransactionCommands {

    private final TransactionService transactionService;
    private final CliUserContext cliUserContext;

    @ShellMethod(key = "transactions list", value = "List recent transactions")
    public String list(
            @ShellOption(defaultValue = "10", help = "Number of transactions to show")
            int limit,
            @ShellOption(defaultValue = ShellOption.NULL, help = "From date (yyyy-MM-dd)")
            String from,
            @ShellOption(defaultValue = ShellOption.NULL, help = "To date (yyyy-MM-dd)")
            String to
    ) {
        LocalDate fromDate = from != null ? LocalDate.parse(from) : null;
        LocalDate toDate = to != null ? LocalDate.parse(to) : null;

        var page = transactionService.getAll(
                cliUserContext.getUserId(), fromDate, toDate,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "date"))
        );

        if (page.content().isEmpty()) {
            return "No transactions found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s  %-10s  %-25s  %s%n", "Date", "Amount CHF", "Description", "Category"));
        sb.append("─".repeat(72)).append("\n");
        page.content().forEach(tx ->
                sb.append(String.format("%-12s  %10s  %-25s  %s%n",
                        tx.date(),
                        tx.amount().toPlainString(),
                        tx.description() != null ? truncate(tx.description(), 25) : "-",
                        tx.categoryName() != null ? tx.categoryName() : "-"
                ))
        );
        sb.append("\nShowing ").append(page.content().size())
          .append(" of ").append(page.totalElements()).append(" transactions");
        return sb.toString();
    }

    @ShellMethod(key = "transactions add", value = "Add a manual transaction")
    public String add(
            @ShellOption(help = "Amount (negative for expense, positive for income)")
            BigDecimal amount,
            @ShellOption(help = "Date (yyyy-MM-dd)")
            String date,
            @ShellOption(help = "Description")
            String description,
            @ShellOption(help = "Account UUID")
            String accountId,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Category UUID")
            String categoryId
    ) {
        var request = new TransactionRequest(
                amount,
                "CHF",
                LocalDate.parse(date),
                description,
                categoryId != null ? UUID.fromString(categoryId) : null,
                UUID.fromString(accountId)
        );
        var result = transactionService.create(request, cliUserContext.getUserId());
        return "Created transaction " + result.id() + " on " + result.date() + " for CHF " + result.amount();
    }

    @ShellMethod(key = "transactions delete", value = "Delete a transaction")
    public String delete(
            @ShellOption(help = "Transaction UUID")
            String id
    ) {
        transactionService.delete(UUID.fromString(id), cliUserContext.getUserId());
        return "Deleted transaction " + id;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
