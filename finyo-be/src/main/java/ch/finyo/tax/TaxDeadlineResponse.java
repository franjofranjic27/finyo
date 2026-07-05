package ch.finyo.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxDeadlineResponse(
        UUID id,
        LocalDate dueDate,
        String label,
        BigDecimal amount,
        boolean done
) {
    public static TaxDeadlineResponse from(TaxDeadline deadline) {
        return new TaxDeadlineResponse(
                deadline.getId(),
                deadline.getDueDate(),
                deadline.getLabel(),
                deadline.getAmount(),
                deadline.isDone()
        );
    }
}
