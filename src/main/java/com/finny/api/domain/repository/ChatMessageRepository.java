package com.finny.api.domain.repository;

import com.finny.api.domain.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    // Buscar histórico de mensagens do usuário com paginação
    Page<ChatMessage> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Buscar últimas N mensagens do usuário (para contexto do chat)
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.user.id = :userId " +
           "ORDER BY cm.createdAt DESC")
    List<ChatMessage> findTopNByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    // Deletar histórico de mensagens do usuário
    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    // Contar mensagens do usuário
    long countByUserId(UUID userId);

    /**
     * Soma total de tokens usados pelo usuário (custo)
     */
    @Query("SELECT COALESCE(SUM(cm.tokensUsed), 0) FROM ChatMessage cm " +
           "WHERE cm.user.id = :userId")
    long sumTokensUsedByUserId(@Param("userId") UUID userId);
}
