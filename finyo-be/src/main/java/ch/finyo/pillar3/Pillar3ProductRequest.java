package ch.finyo.pillar3;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Create/update/import payload for a pillar 3a product. {@code active} and
 * {@code sortOrder} are optional: they default to {@code true}/{@code 0} on
 * create and are only overwritten on update/import when provided.
 */
public record Pillar3ProductRequest(
        @NotBlank @Size(max = 100) String provider,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}[A-Za-z0-9]{9}[0-9]$", message = "must be a valid ISIN (2 letters, 9 alphanumerics, 1 check digit)") String isin,
        @Pattern(regexp = "^\\d{1,20}$", message = "must be a numeric valor of up to 20 digits") String valor,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal equityPct,
        @NotNull @DecimalMin("0") @DecimalMax("10") BigDecimal terPct,
        Boolean active,
        Integer sortOrder
) {}
