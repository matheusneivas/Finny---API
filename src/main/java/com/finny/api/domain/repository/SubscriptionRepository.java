package com.finny.api.domain.repository;

import com.finny.api.domain.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByUserId(UUID userId);

    Optional<Subscription> findByExternalId(String externalId);
}
