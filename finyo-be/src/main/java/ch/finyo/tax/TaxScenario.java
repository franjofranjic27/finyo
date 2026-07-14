package ch.finyo.tax;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Saved scenario of a tax year. Name and tax inputs are editable via the
 * update endpoint; the default flag is switched exclusively via its own
 * endpoint (PATCH /{id}/default), never through an update.
 */
@Entity
@Table(name = "tax_scenario")
@EntityListeners(AuditingEntityListener.class)
@Getter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class TaxScenario extends TaxInputs {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "tax_year_id", nullable = false)
    private UUID taxYearId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
