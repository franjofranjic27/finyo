package ch.finyoapi.tax;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_federal_rate")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxFederalRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(length = 20, nullable = false)
    private String tariff;

    @Column(name = "income_from", precision = 19, scale = 4, nullable = false)
    private BigDecimal incomeFrom;

    @Column(name = "income_to", precision = 19, scale = 4)
    private BigDecimal incomeTo;

    @Column(name = "base_tax", precision = 19, scale = 4, nullable = false)
    private BigDecimal baseTax;

    @Column(name = "rate_per_100", precision = 10, scale = 6, nullable = false)
    private BigDecimal ratePerHundred;
}
