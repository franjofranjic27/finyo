package ch.finyo.insurance;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "insurance_type")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 50, unique = true, nullable = false)
    private String code;

    @Column(name = "name_en", length = 100, nullable = false)
    private String nameEn;

    @Column(name = "name_de", length = 100, nullable = false)
    private String nameDe;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_de", columnDefinition = "TEXT")
    private String descriptionDe;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(nullable = false)
    private boolean recommended;

    @Column(name = "typical_cost_min", precision = 10, scale = 2)
    private BigDecimal typicalCostMin;

    @Column(name = "typical_cost_max", precision = 10, scale = 2)
    private BigDecimal typicalCostMax;

    @Column(name = "comparison_url", length = 500)
    private String comparisonUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
