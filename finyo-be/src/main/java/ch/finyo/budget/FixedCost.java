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
@Table(name = "fixed_cost")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedCost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 50)
    private String category;

    // column deliberately not named "interval" (reserved word in SQL)
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_interval", length = 20, nullable = false)
    private PaymentInterval paymentInterval;

    /** Cost per payment interval as entered (monthly or yearly amount). */
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
