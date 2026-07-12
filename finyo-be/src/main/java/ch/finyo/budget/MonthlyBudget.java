package ch.finyo.budget;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "monthly_budget")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "net_income", precision = 19, scale = 4, nullable = false)
    private BigDecimal netIncome;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal savings;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal investing;

    @Column(name = "pillar3a", precision = 19, scale = 4, nullable = false)
    private BigDecimal pillar3a;

    @Column(name = "tax_reserve", precision = 19, scale = 4, nullable = false)
    private BigDecimal taxReserve;

    @Column(name = "work_costs", precision = 19, scale = 4, nullable = false)
    private BigDecimal workCosts;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
