package ch.finyo.tax;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tax_year")
@EntityListeners(AuditingEntityListener.class)
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class TaxYear extends TaxInputs {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    /** Renamed from "taxYear" to avoid clashing with the class name; the DB column stays "tax_year". */
    @Column(name = "tax_year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TaxYearStatus status;

    @Column(name = "filing_deadline")
    private LocalDate filingDeadline;

    @Column(name = "filed_at")
    private LocalDate filedAt;

    @Column(name = "assessed_at")
    private LocalDate assessedAt;

    @Column(name = "assessed_amount")
    private BigDecimal assessedAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
