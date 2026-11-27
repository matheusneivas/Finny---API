package com.finny.api.domain.repository;

import com.finny.api.domain.entity.EmailCode;
import com.finny.api.domain.enums.EmailCodePurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailCodeRepository extends JpaRepository<EmailCode, UUID> {

    Optional<EmailCode> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, EmailCodePurpose purpose);

    long countByUserIdAndPurposeAndCreatedAtAfter(UUID userId, EmailCodePurpose purpose, OffsetDateTime after);

    /** Invalida códigos anteriores ainda abertos (só o mais recente vale). */
    @Modifying
    @Query("UPDATE EmailCode c SET c.consumedAt = :now WHERE c.userId = :userId AND c.purpose = :purpose AND c.consumedAt IS NULL")
    int consumeOpen(@Param("userId") UUID userId, @Param("purpose") EmailCodePurpose purpose, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM EmailCode c WHERE c.createdAt < :before")
    int deleteCreatedBefore(@Param("before") OffsetDateTime before);
}
