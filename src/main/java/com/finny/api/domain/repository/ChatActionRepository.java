package com.finny.api.domain.repository;

import com.finny.api.domain.entity.ChatAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatActionRepository extends JpaRepository<ChatAction, String> {

    Optional<ChatAction> findByIdAndUserId(String id, UUID userId);

    List<ChatAction> findByMessageIdIn(Collection<UUID> messageIds);

    @Modifying
    @Query("DELETE FROM ChatAction a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
