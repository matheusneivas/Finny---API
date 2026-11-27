package com.finny.api.domain.repository;

import com.finny.api.domain.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
}
