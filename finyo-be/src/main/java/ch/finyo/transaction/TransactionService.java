package ch.finyo.transaction;

import ch.finyo.account.AccountRepository;
import ch.finyo.category.CategoryRepository;
import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String RESOURCE_NAME = "Transaction";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

    // readOnly transaction keeps the session open while lazy account/category
    // proxies are resolved during DTO mapping (open-in-view is disabled)
    @Transactional(readOnly = true)
    public TransactionPageResponse getAll(String userId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<Transaction> page;
        if (from != null && to != null) {
            page = transactionRepository.findByUserIdAndDateBetween(userId, from, to, pageable);
        } else {
            page = transactionRepository.findByUserId(userId, pageable);
        }
        return new TransactionPageResponse(
                page.getContent().stream().map(TransactionResponse::from).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id, String userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request, String userId) {
        var account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        var category = request.categoryId() != null
                ? categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()))
                : null;

        var transaction = Transaction.builder()
                .userId(userId)
                .amount(request.amount())
                .currency(request.currency() != null ? request.currency() : "CHF")
                .date(request.date())
                .description(request.description())
                .category(category)
                .account(account)
                .source(TransactionSource.MANUAL)
                .build();

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request, String userId) {
        var existing = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        var account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        var category = request.categoryId() != null
                ? categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()))
                : null;

        var updated = Transaction.builder()
                .id(existing.getId())
                .userId(userId)
                .amount(request.amount())
                .currency(request.currency() != null ? request.currency() : existing.getCurrency())
                .date(request.date())
                .description(request.description())
                .category(category)
                .account(account)
                .source(existing.getSource())
                // the bank reference must survive manual edits, otherwise a
                // re-import would resurrect the transaction as a duplicate
                .externalRef(existing.getExternalRef())
                .build();

        return TransactionResponse.from(transactionRepository.save(updated));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        if (transactionRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw ResourceNotFoundException.of(RESOURCE_NAME, id);
        }
        transactionRepository.deleteById(id);
    }
}
