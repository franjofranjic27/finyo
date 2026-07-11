package ch.finyo.account;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Payment-card master data (name, provider, linked settlement account, fees).
 *
 * SECURITY: this entity deliberately stores NO sensitive card data — no card
 * number / PAN, no CVV, no expiry date. It only describes which cards exist,
 * not how to use them.
 *
 * accountId is a plain FK column (ON DELETE SET NULL in the DB, no JPA
 * relation): deleting the linked account detaches the card instead of
 * cascading into it.
 */
@Entity
@Table(name = "payment_card")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 50)
    private String provider;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(length = 3)
    private String currency;

    @Column(name = "fee_note", length = 100)
    private String feeNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private AccountScope scope = AccountScope.PRIVATE;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
