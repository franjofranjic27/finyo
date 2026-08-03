package ch.finyo.profile;

import ch.finyo.common.money.CurrencyCode;
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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "salutation", length = 10)
    private Salutation salutation;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "civil_status", length = 20)
    private TaxCivilStatus civilStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "church_affiliation", length = 20)
    private ChurchAffiliation churchAffiliation;

    @Column(name = "nationality", length = 100)
    private String nationality;

    /** Street and house number as one free-text field. */
    @Column(name = "street", length = 200)
    private String street;

    @Column(name = "postal_code", length = 10)
    private String postalCode;

    @Column(name = "city", length = 100)
    private String city;

    /** Wohngemeinde — tax-relevant, deliberately free text. */
    @Column(name = "municipality", length = 100)
    private String municipality;

    /** Two-letter canton abbreviation, same style as the tax tables (e.g. SG). */
    @Column(name = "canton_code", length = 2)
    private String cantonCode;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "preferred_language", length = 5)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 10)
    private Theme theme;

    @Column(name = "default_currency", nullable = false, length = 3)
    private CurrencyCode defaultCurrency;

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
     * The defaults mirror the column defaults in V27/V37: theme SYSTEM,
     * default currency CHF, onboarding not completed, all master data unset.
     */
    static UserProfile withDefaults(String userId) {
        return UserProfile.builder()
                .userId(userId)
                .theme(Theme.SYSTEM)
                .defaultCurrency(CurrencyCode.CHF)
                .onboardingCompleted(false)
                .build();
    }
}
