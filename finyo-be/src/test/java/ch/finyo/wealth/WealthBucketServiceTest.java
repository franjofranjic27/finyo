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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for the MANUAL/PORTFOLIO presence rules, the one-bucket-per-asset-class
 * invariant and the request-to-entity defaults of WealthBucketService.
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

    private static WealthBucketRequest portfolioRequest(String name, List<AssetClass> classes) {
        return new WealthBucketRequest(name, null, WealthSource.PORTFOLIO,
                null, classes, null, null);
    }

    private static WealthBucket portfolioBucket(UUID id, String name, String assetClasses) {
        return WealthBucket.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .source(WealthSource.PORTFOLIO)
                .assetClasses(assetClasses)
                .monthlyRate(BigDecimal.ZERO)
                .sortOrder(0)
                .build();
    }

    // -------------------------------------------------------------------------
    // Source-dependent presence rules
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

    @Test
    void portfolio_bucket_without_asset_classes_is_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(portfolioRequest("Broker", null), USER_ID))
                .withMessageContaining("assetClasses");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(portfolioRequest("Broker", List.of()), USER_ID))
                .withMessageContaining("assetClasses");
        then(bucketRepository).shouldHaveNoInteractions();
    }

    // -------------------------------------------------------------------------
    // Asset-class exclusivity across buckets
    // -------------------------------------------------------------------------

    @Test
    void asset_class_already_linked_to_another_bucket_is_rejected_on_create() {
        given(bucketRepository.existsByUserIdAndName(USER_ID, "New Broker")).willReturn(false);
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(portfolioBucket(UUID.randomUUID(), "Old Broker", "ETF,FUND")));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.create(
                        portfolioRequest("New Broker", List.of(AssetClass.STOCK, AssetClass.FUND)), USER_ID))
                .withMessageContaining("FUND")
                .withMessageContaining("Old Broker");
    }

    @Test
    void update_may_keep_the_asset_classes_of_the_bucket_itself() {
        UUID id = UUID.randomUUID();
        WealthBucket existing = portfolioBucket(id, "Broker", "ETF");
        given(bucketRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(bucketRepository.existsByUserIdAndNameAndIdNot(USER_ID, "Broker", id)).willReturn(false);
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(existing));
        given(bucketRepository.save(any(WealthBucket.class))).willAnswer(inv -> inv.getArgument(0));

        WealthBucketResponse response = bucketService.update(id,
                portfolioRequest("Broker", List.of(AssetClass.ETF, AssetClass.CRYPTO)), USER_ID);

        assertThat(response.assetClasses()).containsExactly(AssetClass.ETF, AssetClass.CRYPTO);
    }

    @Test
    void update_rejects_an_asset_class_linked_to_a_different_bucket() {
        UUID id = UUID.randomUUID();
        WealthBucket existing = portfolioBucket(id, "Broker", "ETF");
        WealthBucket other = portfolioBucket(UUID.randomUUID(), "Crypto Wallet", "CRYPTO");
        given(bucketRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(bucketRepository.existsByUserIdAndNameAndIdNot(USER_ID, "Broker", id)).willReturn(false);
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(existing, other));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> bucketService.update(id,
                        portfolioRequest("Broker", List.of(AssetClass.CRYPTO)), USER_ID))
                .withMessageContaining("CRYPTO")
                .withMessageContaining("Crypto Wallet");
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
    void create_defaults_monthly_rate_and_sort_order_and_stores_classes_as_csv() {
        given(bucketRepository.existsByUserIdAndName(USER_ID, "Broker")).willReturn(false);
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());
        given(bucketRepository.save(any(WealthBucket.class))).willAnswer(inv -> inv.getArgument(0));

        bucketService.create(portfolioRequest("Broker", List.of(AssetClass.ETF, AssetClass.STOCK)), USER_ID);

        ArgumentCaptor<WealthBucket> captor = ArgumentCaptor.forClass(WealthBucket.class);
        then(bucketRepository).should().save(captor.capture());
        WealthBucket saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getAssetClasses()).isEqualTo("ETF,STOCK");
        assertThat(saved.getManualBalance()).isNull();
        assertThat(saved.getMonthlyRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getSortOrder()).isZero();
    }

    @Test
    void create_of_a_manual_bucket_stores_the_balance_and_no_classes() {
        given(bucketRepository.existsByUserIdAndName(USER_ID, "Cash")).willReturn(false);
        given(bucketRepository.save(any(WealthBucket.class))).willAnswer(inv -> inv.getArgument(0));

        WealthBucketResponse response = bucketService.create(manualRequest("Cash", "5000"), USER_ID);

        assertThat(response.source()).isEqualTo(WealthSource.MANUAL);
        assertThat(response.manualBalance()).isEqualByComparingTo("5000");
        assertThat(response.assetClasses()).isEmpty();
    }
}
