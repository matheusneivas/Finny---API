package com.finny.api.application.service;

import com.finny.api.application.dto.request.TransactionRequest;
import com.finny.api.application.dto.response.TransactionResponse;
import com.finny.api.application.dto.response.TransactionSummaryResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.TransactionMapper;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.TransactionSource;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionResponse createTransaction(UUID userId, TransactionRequest request) {
        log.debug("Creating transaction for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        // Validar se categoria pertence ao usuário ou é padrão
        if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
            throw new BusinessException("Categoria não pertence ao usuário");
        }

        Transaction transaction = transactionMapper.toEntity(request);
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setSource(TransactionSource.MANUAL);

        transaction = transactionRepository.save(transaction);

        log.info("Transaction created successfully: {}", transaction.getId());

        return transactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID userId, Pageable pageable) {
        log.debug("Getting transactions for user: {}", userId);

        Page<Transaction> transactions = transactionRepository
                .findByUserIdAndDeletedFalseOrderByTransactionDateDesc(userId, pageable);

        return transactions.map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID userId, UUID transactionId) {
        log.debug("Getting transaction: {} for user: {}", transactionId, userId);

        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada"));

        if (transaction.getDeleted()) {
            throw new EntityNotFoundException("Transação não encontrada");
        }

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID userId, UUID transactionId, TransactionRequest request) {
        log.debug("Updating transaction: {} for user: {}", transactionId, userId);

        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada"));

        if (transaction.getDeleted()) {
            throw new EntityNotFoundException("Transação não encontrada");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

        // Validar se categoria pertence ao usuário ou é padrão
        if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
            throw new BusinessException("Categoria não pertence ao usuário");
        }

        transactionMapper.updateEntityFromRequest(request, transaction);
        transaction.setCategory(category);

        transaction = transactionRepository.save(transaction);

        log.info("Transaction updated successfully: {}", transactionId);

        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID userId, UUID transactionId) {
        log.debug("Deleting (soft) transaction: {} for user: {}", transactionId, userId);

        Transaction transaction = transactionRepository.findByIdAndUserId(transactionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada"));

        transaction.setDeleted(true);
        transactionRepository.save(transaction);

        log.info("Transaction soft deleted: {}", transactionId);
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse getSummary(UUID userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting transaction summary for user: {} from {} to {}", userId, startDate, endDate);

        BigDecimal totalIncome = transactionRepository
                .sumIncomeByUserIdAndDateRange(userId, startDate, endDate);

        BigDecimal totalExpense = transactionRepository
                .sumExpenseByUserIdAndDateRange(userId, startDate, endDate);

        BigDecimal balance = totalIncome.subtract(totalExpense);

        long transactionCount = transactionRepository.countByUserIdAndDeletedFalse(userId);

        return TransactionSummaryResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .balance(balance)
                .transactionCount(transactionCount)
                .build();
    }
}
