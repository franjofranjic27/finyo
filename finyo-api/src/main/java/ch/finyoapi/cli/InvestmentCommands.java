package ch.finyoapi.cli;

import ch.finyoapi.investment.InstrumentRequest;
import ch.finyoapi.investment.InstrumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.UUID;

@ShellComponent
@RequiredArgsConstructor
public class InvestmentCommands {

    private final InstrumentService instrumentService;
    private final CliUserContext cliUserContext;

    @ShellMethod(key = "investments list", value = "List all tracked investment instruments")
    public String list() {
        var instruments = instrumentService.getAll(cliUserContext.getUserId());
        if (instruments.isEmpty()) {
            return "No investment instruments tracked.";
        }

        StringBuilder sb = new StringBuilder("Investment Instruments\n");
        sb.append("─".repeat(75)).append("\n");
        sb.append(String.format("%-36s  %-8s  %-12s  %-12s  %s%n",
                "ID", "Type", "VALOR", "Last Price", "Name"));
        sb.append("─".repeat(75)).append("\n");
        instruments.forEach(i -> {
            String price = i.lastPrice() != null ? String.format("CHF %,.2f", i.lastPrice()) : "-";
            String valor = i.valor() != null ? i.valor() : (i.isin() != null ? i.isin() : "-");
            sb.append(String.format("%-36s  %-8s  %-12s  %-12s  %s%n",
                    i.id(), i.instrumentType(), valor, price, truncate(i.name(), 25)));
        });
        return sb.toString();
    }

    @ShellMethod(key = "investments add", value = "Add an investment instrument by VALOR number")
    public String add(
            @ShellOption(help = "SIX VALOR number")
            String valor,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Instrument name (optional)")
            String name,
            @ShellOption(defaultValue = ShellOption.NULL, help = "Instrument type: STOCK, ETF, BOND, FUND, COMMODITY, CRYPTO, OTHER")
            String type
    ) {
        ch.finyoapi.investment.InstrumentType instrumentType = type != null
                ? ch.finyoapi.investment.InstrumentType.valueOf(type.toUpperCase())
                : ch.finyoapi.investment.InstrumentType.OTHER;

        var request = new InstrumentRequest(valor, null, null, name, instrumentType, null);
        var result = instrumentService.create(request, cliUserContext.getUserId());
        return String.format("Added instrument '%s' (id: %s, VALOR: %s)",
                result.name() != null ? result.name() : valor, result.id(), result.valor());
    }

    @ShellMethod(key = "investments remove", value = "Remove a tracked investment instrument")
    public String remove(
            @ShellOption(help = "Instrument UUID")
            String id
    ) {
        instrumentService.delete(UUID.fromString(id), cliUserContext.getUserId());
        return "Removed instrument " + id;
    }

    private String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max - 1) + "..." : s;
    }
}
