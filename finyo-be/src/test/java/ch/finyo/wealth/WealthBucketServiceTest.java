package ch.finyo.wealth;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.investment.AssetClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for the MANUAL-only rule (portfolio and pillar 3a rows are
 * synthesized in the overview, never persisted), the presence rules and the
 * request-to-entity defaults of WealthBucketService.
 */
@ExtendWith(MockitoExtension.class)
class WealthBucketServiceTest {

    private static final String USER_ID = "user-wealth-1";

    @Mock
    private WealthBucketRepository bucketRepository;

    @InjectMocks
    private WealthBucketService bucketService;

    private static WealthBucketRequest manualRequest(String name, String balance) {
        return new WealthBucketRequest(name, null, WealthSource.MANUAL,
                balance != null ? new BigDecimal(balance) : null, null, null, null);
    }

    private static WealthBucketRequest requestWithSource(WealthSource source) {
        return new WealthBucketRequest("Bucket", null, source, null, null, null, null);
    }

    private static WealthBucket manualBucket(UUID id, String name) {
        return WealthBucket.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .source(WealthSource.MANUAL)
                .manualBalance(new BigDecimal("100"))
                .monthlyRate(BigDecimal.ZERO)
                .sortOrder(0)
                .build();
    }

    // -------------------------------------------------------------------------
    // MANUAL-only rule
    // -------------------------------------------------------------------------

    @Test
    void portfolio_source_is_rejected_on_create() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(requestWithSource(WealthSource.PORTFOLIO), USER_ID))
                .withMessageContaining("Only MANUAL wealth buckets");
        then(bucketRepository).shouldHaveNoInteractions();
    }

    @Test
    void pillar3_source_is_rejected_on_create() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(requestWithSource(WealthSource.PILLAR3), USER_ID))
                .withMessageContaining("Only MANUAL wealth buckets");
        then(bucketRepository).shouldHaveNoInteractions();
    }

    @Test
    void non_manual_source_is_rejected_on_update_of_an_existing_bucket() {
        UUID id = UUID.randomUUID();
        given(bucketRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(manualBucket(id, "Cash")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.update(id,
                        requestWithSource(WealthSource.PORTFOLIO), USER_ID))
                .withMessageContaining("Only MANUAL wealth buckets");
    }

    // -------------------------------------------------------------------------
    // Presence rules for MANUAL requests
    // -------------------------------------------------------------------------

    @Test
    void manual_bucket_without_balance_is_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(manualRequest("Cash", null), USER_ID))
                .withMessageContaining("manualBalance");
        then(bucketRepository).shouldHaveNoInteractions();
    }

    @Test
    void manual_bucket_with_asset_classes_is_rejected() {
        var request = new WealthBucketRequest("Cash", null, WealthSource.MANUAL,
                new BigDecimal("100"), List.of(AssetClass.ETF), null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(request, USER_ID))
                .withMessageContaining("assetClasses");
        then(bucketRepository).shouldHaveNoInteractions();
    }

    // -------------------------------------------------------------------------
    // Duplicate names & not-found
    // -------------------------------------------------------------------------

    @Test
    void duplicate_name_is_rejected_on_create() {
        given(bucketRepository.existsByUserIdAndName(USER_ID, "Cash")).willReturn(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(manualRequest("Cash", "100"), USER_ID))
                .withMessageContaining("Cash");
    }

    @Test
    void update_of_a_missing_bucket_fails_with_not_found() {
        UUID id = UUID.randomUUID();
        given(bucketRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> bucketService.update(id, manualRequest("Cash", "100"), USER_ID));
    }

    @Test
    void delete_of_a_missing_bucket_fails_with_not_found() {
        UUID id = UUID.randomUUID();
        given(bucketRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
                .isThrownBy(() -> bucketService.delete(id, USER_ID));
    }

    // -------------------------------------------------------------------------
    // Entity mapping defaults
    // -------------------------------------------------------------------------

    @Test
    void create_stores_the_balance_and_defaults_monthly_rate_and_sort_order() {
        given(bucketRepository.existsByUserIdAndName(USER_ID, "Cash")).willReturn(false);
        given(bucketRepository.save(any(WealthBucket.class))).willAnswer(inv -> inv.getArgument(0));

        WealthBucketResponse response = bucketService.create(manualRequest("Cash", "5000"), USER_ID);

        ArgumentCaptor<WealthBucket> captor = ArgumentCaptor.forClass(WealthBucket.class);
        then(bucketRepository).should().save(captor.capture());
        WealthBucket saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSource()).isEqualTo(WealthSource.MANUAL);
        assertThat(saved.getAssetClasses()).isNull();
        assertThat(saved.getMonthlyRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getSortOrder()).isZero();
        assertThat(response.manualBalance()).isEqualByComparingTo("5000");
        assertThat(response.assetClasses()).isEmpty();
    }

    @Test
    void update_keeps_the_id_and_overwrites_the_stored_fields() {
        UUID id = UUID.randomUUID();
        given(bucketRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(manualBucket(id, "Cash")));
        given(bucketRepository.existsByUserIdAndNameAndIdNot(USER_ID, "Emergency Fund", id))
                .willReturn(false);
        given(bucketRepository.save(any(WealthBucket.class))).willAnswer(inv -> inv.getArgument(0));

        WealthBucketResponse response = bucketService.update(id,
                manualRequest("Emergency Fund", "7500"), USER_ID);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Emergency Fund");
        assertThat(response.manualBalance()).isEqualByComparingTo("7500");
    }
}
