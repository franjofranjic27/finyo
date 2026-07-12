package ch.finyo.account;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String RESOURCE_NAME = "Account";
    private static final Pattern BIC_FORMAT = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");

    private final AccountRepository accountRepository;

    public List<AccountResponse> getAll(String userId) {
        log.debug("Fetching all accounts for user={}", userId);
        return accountRepository.findByUserIdOrderByNameAsc(userId)
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    public AccountResponse getById(UUID id, String userId) {
        log.debug("Fetching account id={} for user={}", id, userId);
        return accountRepository.findByIdAndUserId(id, userId)
                .map(AccountResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
    }

    @Transactional
    public AccountResponse create(AccountRequest request, String userId) {
        log.info("Creating account name='{}' type={} for user={}", request.name(), request.type(), userId);
        Account account = Account.builder()
                .userId(userId)
                .name(request.name())
                .type(request.type())
                .currency(request.currency() != null ? request.currency() : "CHF")
                .initialBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO)
                .color(request.color())
                .iban(normalizedIbanOrNull(request.iban()))
                .bic(normalizedBicOrNull(request.bic()))
                .contractNumber(request.contractNumber())
                .feeNote(request.feeNote())
                .scope(request.scope() != null ? request.scope() : AccountScope.PRIVATE)
                .toClose(Boolean.TRUE.equals(request.toClose()))
                .build();
        Account saved = accountRepository.save(account);
        log.info("Created account id={} for user={}", saved.getId(), userId);
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request, String userId) {
        log.info("Updating account id={} for user={}", id, userId);
        Account existing = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        // toBuilder keeps id/userId/createdAt; PUT replaces all client-editable fields.
        // Exception: currency/initialBalance keep their stored value when omitted, because
        // the columns are NOT NULL and pre-V24 clients may not send them on every update.
        Account updated = existing.toBuilder()
                .name(request.name())
                .type(request.type())
                .currency(request.currency() != null ? request.currency() : existing.getCurrency())
                .initialBalance(request.initialBalance() != null ? request.initialBalance() : existing.getInitialBalance())
                .color(request.color())
                .iban(normalizedIbanOrNull(request.iban()))
                .bic(normalizedBicOrNull(request.bic()))
                .contractNumber(request.contractNumber())
                .feeNote(request.feeNote())
                .scope(request.scope() != null ? request.scope() : AccountScope.PRIVATE)
                .toClose(Boolean.TRUE.equals(request.toClose()))
                .build();

        Account saved = accountRepository.save(updated);
        log.info("Updated account id={} for user={}", saved.getId(), userId);
        return AccountResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting account id={} for user={}", id, userId);
        if (!accountRepository.existsByIdAndUserId(id, userId)) {
            throw ResourceNotFoundException.of(RESOURCE_NAME, id);
        }
        accountRepository.deleteById(id);
        log.info("Deleted account id={} for user={}", id, userId);
    }

    private static String normalizedIbanOrNull(String iban) {
        String normalized = IbanValidator.normalize(iban);
        if (normalized == null) {
            return null;
        }
        if (!IbanValidator.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid IBAN");
        }
        return normalized;
    }

    private static String normalizedBicOrNull(String bic) {
        if (bic == null || bic.isBlank()) {
            return null;
        }
        String normalized = bic.trim().toUpperCase(Locale.ROOT);
        if (!BIC_FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid BIC");
        }
        return normalized;
    }
}
