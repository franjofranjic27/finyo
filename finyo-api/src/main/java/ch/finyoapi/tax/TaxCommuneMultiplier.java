package ch.finyoapi.tax;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tax_commune_multiplier")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCommuneMultiplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Column(name = "canton_code", length = 2, nullable = false)
    private String cantonCode;

    @Column(name = "commune_name", length = 100, nullable = false)
    private String communeName;

    @Column(name = "bfs_number", nullable = false)
    private int bfsNumber;

    @Column(precision = 8, scale = 4, nullable = false)
    private BigDecimal multiplier;
}
