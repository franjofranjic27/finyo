package ch.finyo.investment;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "instrument")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(length = 20)
    private String valor;

    @Column(length = 12)
    private String isin;

    @Column(length = 20)
    private String ticker;

    @Column(length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type")
    private InstrumentType instrumentType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "last_price", precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "last_price_updated_at")
    private OffsetDateTime lastPriceUpdatedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
