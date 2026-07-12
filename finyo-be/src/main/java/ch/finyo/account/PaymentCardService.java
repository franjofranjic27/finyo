package ch.finyo.account;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCardService {

    private static final String RESOURCE_NAME = "PaymentCard";

    private final PaymentCardRepository paymentCardRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<PaymentCardResponse> getAll(String userId) {
        log.debug("Fetching all payment cards for user={}", userId);
        // One accounts query for the whole list instead of one lookup per card.
        Map<UUID, String> accountNamesById = accountRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        return paymentCardRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(card -> PaymentCardResponse.from(card,
                        card.getAccountId() != null ? accountNamesById.get(card.getAccountId()) : null))
                .toList();
    }

    @Transactional
    public PaymentCardResponse create(PaymentCardRequest request, String userId) {
        log.info("Creating payment card name='{}' for user={}", request.name(), userId);
        String accountName = resolveOwnedAccountName(request.accountId(), userId);
        PaymentCard card = PaymentCard.builder()
                .userId(userId)
                .name(request.name())
                .provider(request.provider())
                .accountId(request.accountId())
                .currency(request.currency())
                .feeNote(request.feeNote())
                .scope(request.scope() != null ? request.scope() : AccountScope.PRIVATE)
                .build();
        PaymentCard saved = paymentCardRepository.save(card);
        log.info("Created payment card id={} for user={}", saved.getId(), userId);
        return PaymentCardResponse.from(saved, accountName);
    }

    @Transactional
    public PaymentCardResponse update(UUID id, PaymentCardRequest request, String userId) {
        log.info("Updating payment card id={} for user={}", id, userId);
        PaymentCard existing = paymentCardRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        String accountName = resolveOwnedAccountName(request.accountId(), userId);

        PaymentCard updated = existing.toBuilder()
                .name(request.name())
                .provider(request.provider())
                .accountId(request.accountId())
                .currency(request.currency())
                .feeNote(request.feeNote())
                .scope(request.scope() != null ? request.scope() : AccountScope.PRIVATE)
                .build();

        PaymentCard saved = paymentCardRepository.save(updated);
        log.info("Updated payment card id={} for user={}", saved.getId(), userId);
        return PaymentCardResponse.from(saved, accountName);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting payment card id={} for user={}", id, userId);
        if (!paymentCardRepository.existsByIdAndUserId(id, userId)) {
            throw ResourceNotFoundException.of(RESOURCE_NAME, id);
        }
        paymentCardRepository.deleteById(id);
        log.info("Deleted payment card id={} for user={}", id, userId);
    }

    /**
     * A card may only ever be linked to an account of the same user. Foreign
     * or unknown accounts are rejected with the same generic message so the
     * response does not reveal whether the account exists at all.
     */
    private String resolveOwnedAccountName(UUID accountId, String userId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .map(Account::getName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account"));
    }
}
