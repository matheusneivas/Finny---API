package com.finny.api.domain.repository;

import com.finny.api.domain.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

    @Query("SELECT t.token FROM UserDeviceToken t WHERE t.user.id = :userId")
    List<String> findTokensByUserId(@Param("userId") UUID userId);

    boolean existsByToken(String token);

    java.util.Optional<UserDeviceToken> findByToken(String token);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM UserDeviceToken t WHERE t.token = :token AND t.user.id = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") UUID userId);

    void deleteByToken(String token);
}
