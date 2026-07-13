package ch.finyo.profile;

import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_profile")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "civil_status", length = 20)
    private TaxCivilStatus civilStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "church_affiliation", length = 20)
    private ChurchAffiliation churchAffiliation;

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private Theme theme;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Unsaved default profile for users without a user_profile row yet.
     * The defaults mirror the column defaults in V27: theme SYSTEM,
     * onboarding not completed, all master data unset.
     */
    static UserProfile withDefaults(String userId) {
        return UserProfile.builder()
                .userId(userId)
                .theme(Theme.SYSTEM)
                .onboardingCompleted(false)
                .build();
    }
}
