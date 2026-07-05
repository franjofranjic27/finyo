package ch.finyo.tax;

public enum TaxYearStatus {
    OPEN,
    FILED,
    ASSESSED,
    PAID;

    /**
     * A tax year may advance any number of steps forward but only be
     * corrected exactly one step backward (e.g. undo an accidental filing).
     */
    public boolean canTransitionTo(TaxYearStatus target) {
        int steps = target.ordinal() - ordinal();
        return steps > 0 || steps == -1;
    }
}
