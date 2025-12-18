package com.finny.api.application.service;

import com.finny.api.application.dto.request.UpdateProfileRequest;
import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.ProfileMapper;
import com.finny.api.domain.entity.UserProfile;
import com.finny.api.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final UserProfileRepository userProfileRepository;
    private final ProfileMapper profileMapper;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        log.debug("Getting profile for user: {}", userId);

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));

        return profileMapper.toResponse(profile);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.debug("Updating profile for user: {}", userId);

        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Perfil não encontrado"));

        profileMapper.updateEntityFromRequest(request, profile);
        profile = userProfileRepository.save(profile);

        log.info("Profile updated successfully for user: {}", userId);

        return profileMapper.toResponse(profile);
    }
}
