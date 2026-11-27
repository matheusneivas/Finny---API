package com.finny.api.domain.repository;

import com.finny.api.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndKey(UUID userId, String key);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :before")
    int deleteCreatedBefore(@Param("before") OffsetDateTime before);
}
