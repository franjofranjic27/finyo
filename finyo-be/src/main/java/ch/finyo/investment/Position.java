package ch.finyo.investment;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A portfolio position: how much of an instrument a user holds and at which
 * average purchase price. References the instrument by plain UUID (no JPA
 * relationship) in line with the rest of the codebase.
 */
@Entity
@Table(name = "position")
@EntityListeners(AuditingEntityListener.class)
@Getter
// toBuilder: entities are immutable — every update copies via toBuilder() so
// new columns can never be wiped by a forgotten field in a manual copy.
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
