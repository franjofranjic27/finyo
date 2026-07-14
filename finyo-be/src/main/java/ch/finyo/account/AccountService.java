package ch.finyo.account;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Account saved = accountRepository.save(newAccount(request, userId));
        log.info("Created account id={} for user={}", saved.getId(), userId);
        return AccountResponse.from(saved);
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request, String userId) {
        log.info("Updating account id={} for user={}", id, userId);
        Account existing = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        Account saved = accountRepository.save(applyRequest(existing, request));
        log.info("Updated account id={} for user={}", saved.getId(), userId);
        return AccountResponse.from(saved);
    }

    /**
     * Bulk upsert: each item is matched against the user's existing accounts by
     * normalized IBAN when the item carries one, otherwise by normalized name
     * (trimmed, lowercased). A match updates that account with the same replace
     * semantics as {@link #update} — an IBAN match may rename the account —
     * otherwise a new account is created with the same defaults as
     * {@link #create}. Items later in the batch that resolve to the same key
     * update the account an earlier item just wrote (last one wins). A failing
     * row is reported in the result without aborting the batch.
     */
    @Transactional
    public AccountBulkResult bulkUpsert(AccountBulkRequest request, String userId) {
        List<AccountRequest> items = request.items();
        log.info("Bulk importing {} account rows for user={}", items.size(), userId);

        Map<String, Account> byIban = new HashMap<>();
        Map<String, Account> byNormalizedName = new HashMap<>();
        loadExistingIntoMaps(userId, byIban, byNormalizedName);

        int created = 0;
        int updated = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            AccountRequest item = items.get(i);
            try {
                Account existing = findMatch(item, byIban, byNormalizedName);
                Account saved = accountRepository.save(existing != null
                        ? applyRequest(existing, item)
                        : newAccount(item, userId));
                if (existing != null) {
                    updated++;
                } else {
                    created++;
                }
                if (saved.getIban() != null) {
                    byIban.put(saved.getIban(), saved);
                }
                byNormalizedName.put(normalizeName(saved.getName()), saved);
                log.debug("Bulk row {}: {} account name='{}' for user={}",
                        i + 1, existing != null ? "updated" : "created", item.name(), userId);
            } catch (IllegalArgumentException e) {
                // Domain validation (e.g. invalid IBAN/BIC) — safe to echo.
                failed++;
                errors.add("row " + (i + 1) + ": " + e.getMessage());
                log.warn("Bulk account import row {} rejected for user={}: {}", i + 1, userId, e.getMessage());
            } catch (Exception e) {
                // Unexpected (e.g. persistence) failures must not echo internals to the client.
                failed++;
                errors.add("row " + (i + 1) + ": persistence error");
                log.error("Bulk account import row {} failed for user={}", i + 1, userId, e);
            }
        }

        log.info("Bulk account import finished for user={}: created={} updated={} failed={}",
                userId, created, updated, failed);
        return new AccountBulkResult(created, updated, failed, List.copyOf(errors));
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

    /**
     * Maps the user's accounts by stored (already normalized) IBAN and by
     * normalized name. When several existing rows share a key, the oldest one
     * (by created_at) wins as the upsert target; the others are left untouched.
     */
    private void loadExistingIntoMaps(String userId, Map<String, Account> byIban,
            Map<String, Account> byNormalizedName) {
        accountRepository.findByUserIdOrderByNameAsc(userId).stream()
                .sorted(Comparator.comparing(Account::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(account -> {
                    if (account.getIban() != null) {
                        byIban.putIfAbsent(account.getIban(), account);
                    }
                    byNormalizedName.putIfAbsent(normalizeName(account.getName()), account);
                });
    }

    /**
     * IBAN is the stronger key: an item that carries one matches only by IBAN
     * (so a rename still hits the right account); IBAN-less items fall back to
     * the normalized name.
     */
    private static Account findMatch(AccountRequest item, Map<String, Account> byIban,
            Map<String, Account> byNormalizedName) {
        String normalizedIban = IbanValidator.normalize(item.iban());
        if (normalizedIban != null) {
            return byIban.get(normalizedIban);
        }
        return byNormalizedName.get(normalizeName(item.name()));
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static Account newAccount(AccountRequest request, String userId) {
        return Account.builder()
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
    }

    /**
     * toBuilder keeps id/userId/createdAt; PUT replaces all client-editable fields.
     * Exception: currency/initialBalance keep their stored value when omitted, because
     * the columns are NOT NULL and pre-V24 clients may not send them on every update.
     */
    private static Account applyRequest(Account existing, AccountRequest request) {
        return existing.toBuilder()
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
