package ch.finyo.salary;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "salary_profile")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_mode", nullable = false, length = 10)
    private SalaryInputMode inputMode;

    @Column(name = "gross_monthly", precision = 19, scale = 4, nullable = false)
    private BigDecimal grossMonthly;

    @Column(name = "gross_yearly", precision = 19, scale = 4, nullable = false)
    private BigDecimal grossYearly;

    @Column(name = "thirteenth_salary", nullable = false)
    private boolean thirteenthSalary;

    @Column(name = "ahv_pct", precision = 7, scale = 4, nullable = false)
    private BigDecimal ahvPct;

    @Column(name = "alv_pct", precision = 7, scale = 4, nullable = false)
    private BigDecimal alvPct;

    @Column(name = "nbu_pct", precision = 7, scale = 4, nullable = false)
    private BigDecimal nbuPct;

    @Column(name = "ktg_pct", precision = 7, scale = 4, nullable = false)
    private BigDecimal ktgPct;

    @Column(name = "pension_fixed", precision = 19, scale = 4, nullable = false)
    private BigDecimal pensionFixed;

    @Column(name = "other_fixed", precision = 19, scale = 4, nullable = false)
    private BigDecimal otherFixed;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Unsaved default profile for users without a salary_profile row yet.
     * The rate defaults mirror the column defaults in V25 (common Swiss
     * employee contributions: AHV/IV/EO 5.3%, ALV 1.1%, NBU 0.455%, KTG 0.17%);
     * the input-mode defaults mirror V28 (MONTHLY, gross_yearly 0).
     */
    static SalaryProfile withDefaults(String userId) {
        return SalaryProfile.builder()
                .userId(userId)
                .inputMode(SalaryInputMode.MONTHLY)
                .grossMonthly(BigDecimal.ZERO)
                .grossYearly(BigDecimal.ZERO)
                .thirteenthSalary(false)
                .ahvPct(new BigDecimal("5.3"))
                .alvPct(new BigDecimal("1.1"))
                .nbuPct(new BigDecimal("0.455"))
                .ktgPct(new BigDecimal("0.17"))
                .pensionFixed(BigDecimal.ZERO)
                .otherFixed(BigDecimal.ZERO)
                .build();
    }
}
