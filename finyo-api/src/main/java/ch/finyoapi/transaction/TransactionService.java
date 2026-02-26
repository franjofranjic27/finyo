package ch.finyoapi.transaction;

import ch.finyoapi.account.AccountRepository;
import ch.finyoapi.category.CategoryRepository;
import ch.finyoapi.common.ResourceNotFoundException;
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

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;

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

    public TransactionResponse getById(UUID id, String userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Transaction", id));
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
                .orElseThrow(() -> ResourceNotFoundException.of("Transaction", id));

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
                .build();

        return TransactionResponse.from(transactionRepository.save(updated));
    }

    @Transactional
    public void delete(UUID id, String userId) {
        if (transactionRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw ResourceNotFoundException.of("Transaction", id);
        }
        transactionRepository.deleteById(id);
    }
}
