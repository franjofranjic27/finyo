package ch.finyoapi.insurance;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_insurance_status")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInsuranceStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "insurance_type_id", nullable = false)
    private InsuranceType insuranceType;

    @Column(name = "has_insurance", nullable = false)
    private boolean hasInsurance;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
