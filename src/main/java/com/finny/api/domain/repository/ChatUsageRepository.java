package com.finny.api.domain.repository;

import com.finny.api.domain.entity.ChatUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatUsageRepository extends JpaRepository<ChatUsage, UUID> {

    Optional<ChatUsage> findByUserIdAndPeriod(UUID userId, String period);

    /** Incrementa de forma atômica somente se ainda estiver abaixo do limite. Retorna linhas afetadas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ChatUsage u SET u.messageCount = u.messageCount + 1 " +
           "WHERE u.userId = :userId AND u.period = :period AND u.messageCount < :limit")
    int incrementIfBelow(@Param("userId") UUID userId, @Param("period") String period, @Param("limit") long limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ChatUsage u SET u.messageCount = u.messageCount - 1 " +
           "WHERE u.userId = :userId AND u.period = :period AND u.messageCount > 0")
    int decrement(@Param("userId") UUID userId, @Param("period") String period);
}
