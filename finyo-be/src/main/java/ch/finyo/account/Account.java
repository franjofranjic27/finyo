package ch.finyo.account;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(name = "initial_balance", nullable = false)
    private BigDecimal initialBalance;

    @Column(length = 7)
    private String color;

    /** Stored normalized: no spaces, uppercase (see IbanValidator). */
    @Column(length = 34)
    private String iban;

    @Column(length = 11)
    private String bic;

    @Column(name = "contract_number", length = 50)
    private String contractNumber;

    @Column(name = "fee_note", length = 100)
    private String feeNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private AccountScope scope = AccountScope.PRIVATE;

    @Column(name = "to_close", nullable = false)
    @Builder.Default
    private boolean toClose = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
