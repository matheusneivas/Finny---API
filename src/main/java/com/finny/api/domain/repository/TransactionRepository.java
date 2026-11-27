package com.finny.api.domain.repository;

import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Buscar transação por ID e usuário (tenant isolation)
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    // Buscar transações do usuário (não deletadas)
    Page<Transaction> findByUserIdAndDeletedFalseOrderByTransactionDateDesc(UUID userId, Pageable pageable);

    // Buscar transações por período
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.deleted = false " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // Buscar transações por tipo
    Page<Transaction> findByUserIdAndTypeAndDeletedFalseOrderByTransactionDateDesc(
        UUID userId,
        TransactionType type,
        Pageable pageable
    );

    // Buscar transações por categoria
    Page<Transaction> findByUserIdAndCategoryIdAndDeletedFalseOrderByTransactionDateDesc(
        UUID userId,
        UUID categoryId,
        Pageable pageable
    );

    // Calcular total de receitas em um período
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.type = 'INCOME' AND t.deleted = false " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumIncomeByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // Calcular total de despesas em um período
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId " +
           "AND t.type = 'EXPENSE' AND t.deleted = false " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumExpenseByUserIdAndDateRange(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    // Contar transações do usuário
    long countByUserIdAndDeletedFalse(UUID userId);

    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, java.time.LocalDateTime start);

    /**
     * Busca transações com filtros múltiplos
     * @param userId ID do usuário (obrigatório - tenant isolation)
     * @param startDate Data inicial (opcional)
     * @param endDate Data final (opcional)
     * @param type Tipo de transação - INCOME ou EXPENSE (opcional)
     * @param categoryId ID da categoria (opcional)
     * @param pageable Paginação
     * @return Página de transações filtradas
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.deleted = false " +
           "AND (:startDate IS NULL OR t.transactionDate >= :startDate) " +
           "AND (:endDate IS NULL OR t.transactionDate <= :endDate) " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:categoryId IS NULL OR t.category.id = :categoryId) " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    Page<Transaction> findByFilters(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("type") TransactionType type,
        @Param("categoryId") UUID categoryId,
        Pageable pageable
    );

    /**
     * Calcula resumo financeiro (total de receitas e despesas)
     * @param userId ID do usuário
     * @param startDate Data inicial
     * @param endDate Data final
     * @return Array com [totalReceitas, totalDespesas]
     */
    @Query("SELECT " +
           "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) " +
           "FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.deleted = false " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    List<BigDecimal> calculateSummary(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Busca transações não deletadas de um usuário
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.deleted = false " +
           "ORDER BY t.transactionDate DESC")
    Page<Transaction> findActiveByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Conta transações ativas de um usuário
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.deleted = false")
    long countActiveByUserId(@Param("userId") UUID userId);

    /**
     * Busca transações por categoria
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.category.id = :categoryId " +
           "AND t.deleted = false")
    List<Transaction> findByCategoryId(
        @Param("userId") UUID userId,
        @Param("categoryId") UUID categoryId
    );

    /**
     * Verifica se usuário é dono da transação
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM Transaction t " +
           "WHERE t.id = :transactionId " +
           "AND t.user.id = :userId")
    boolean existsByIdAndUserId(
        @Param("transactionId") UUID transactionId,
        @Param("userId") UUID userId
    );

    @Query("SELECT t FROM Transaction t JOIN FETCH t.category JOIN FETCH t.user " +
           "WHERE t.dueDayOfMonth = :day AND t.deleted = false")
    List<Transaction> findByDueDayOfMonth(@Param("day") Integer day);

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.recurrenceGroupId = :groupId AND t.user.id = :userId AND t.deleted = false")
    List<Transaction> findByRecurrenceGroupIdAndUserId(
        @Param("groupId") UUID groupId,
        @Param("userId") UUID userId
    );

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.recurrenceGroupId = :groupId AND t.user.id = :userId " +
           "AND t.transactionDate >= :fromDate AND t.deleted = false")
    List<Transaction> findByRecurrenceGroupIdAndUserIdFromDate(
        @Param("groupId") UUID groupId,
        @Param("userId") UUID userId,
        @Param("fromDate") LocalDate fromDate
    );

    @Query("SELECT t FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.deleted = false " +
           "AND t.transactionDate >= :startDate " +
           "AND t.transactionDate <= :endDate " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    Page<Transaction> findByUserIdAndDateRangePaged(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    /**
     * Agrupa despesas por categoria em um período
     * Retorna: [categoryId, categoryName, categoryIcon, categoryColor, total, count]
     */
    @Query("SELECT t.category.id, t.category.name, t.category.icon, t.category.color, " +
           "SUM(t.amount), COUNT(t) " +
           "FROM Transaction t " +
           "WHERE t.user.id = :userId " +
           "AND t.type = 'EXPENSE' " +
           "AND t.deleted = false " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY t.category.id, t.category.name, t.category.icon, t.category.color " +
           "ORDER BY SUM(t.amount) DESC")
    List<Object[]> sumExpenseGroupedByCategory(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
