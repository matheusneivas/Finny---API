package com.finny.api.domain.repository;

import com.finny.api.domain.entity.OcrProcessingQueue;
import com.finny.api.domain.enums.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcrProcessingQueueRepository extends JpaRepository<OcrProcessingQueue, UUID> {

    // Buscar por callback token (usado pelo n8n)
    Optional<OcrProcessingQueue> findByCallbackToken(String callbackToken);

    // Buscar processamentos do usuário
    List<OcrProcessingQueue> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Buscar processamentos pendentes do usuário
    List<OcrProcessingQueue> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, ProcessingStatus status);

    // Buscar por ID e usuário (tenant isolation)
    Optional<OcrProcessingQueue> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Conta processamentos OCR do usuário no mês atual
     */
    @Query("SELECT COUNT(q) FROM OcrProcessingQueue q " +
           "WHERE q.user.id = :userId " +
           "AND q.status = 'COMPLETED' " +
           "AND q.createdAt >= :startOfMonth")
    long countCompletedInCurrentMonth(
        @Param("userId") UUID userId,
        @Param("startOfMonth") LocalDateTime startOfMonth
    );

    /**
     * Busca registros pendentes há mais de X minutos (para cleanup/timeout)
     */
    @Query("SELECT q FROM OcrProcessingQueue q " +
           "WHERE q.status = 'PENDING' " +
           "AND q.createdAt < :timeoutThreshold")
    List<OcrProcessingQueue> findPendingOlderThan(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    /**
     * Verifica se usuário é dono do registro OCR
     */
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END " +
           "FROM OcrProcessingQueue q " +
           "WHERE q.id = :id " +
           "AND q.user.id = :userId")
    boolean existsByIdAndUserId(
        @Param("id") UUID id,
        @Param("userId") UUID userId
    );
}
