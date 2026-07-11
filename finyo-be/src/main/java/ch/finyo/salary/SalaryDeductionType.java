package ch.finyo.salary;

/**
 * Deduction lines of the payslip, in the exact order the frontend renders them.
 * AHV, ALV, NBU and KTG are percentage-based; PENSION and OTHER are fixed amounts.
 */
public enum SalaryDeductionType {
    AHV,
    ALV,
    NBU,
    KTG,
    PENSION,
    OTHER
}
