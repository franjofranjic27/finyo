package ch.finyo.transaction;

public record CsvColumnMapping(
        int dateColumn,
        int amountColumn,
        int descriptionColumn,
        int currencyColumn,       // -1 if not present
        String dateFormat,        // e.g. "dd.MM.yyyy", "yyyy-MM-dd"
        String decimalSeparator,  // "." or ","
        String groupingSeparator, // "'" or "," or "."
        boolean hasHeader,
        String preset             // "UBS", "RAIFFEISEN", "POSTFINANCE", "ZKB", "CUSTOM"
) {
    public static CsvColumnMapping ubs() {
        return new CsvColumnMapping(0, 2, 4, -1, "dd.MM.yyyy", ".", "'", true, "UBS");
    }

    public static CsvColumnMapping raiffeisen() {
        return new CsvColumnMapping(0, 1, 2, -1, "dd.MM.yyyy", ".", "'", true, "RAIFFEISEN");
    }

    public static CsvColumnMapping postfinance() {
        return new CsvColumnMapping(0, 2, 1, -1, "dd.MM.yyyy", ".", "'", true, "POSTFINANCE");
    }
}
