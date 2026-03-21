package com.finny.api.application.mapper;

import com.finny.api.application.dto.request.OnboardingRequest;
import com.finny.api.application.dto.request.UpdateProfileRequest;
import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.domain.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    @Mapping(source = "user.fullName", target = "fullName")
    ProfileResponse toResponse(UserProfile profile);
    UserProfile toEntity(OnboardingRequest request);
    void updateEntityFromRequest(UpdateProfileRequest request, @MappingTarget UserProfile profile);
}
