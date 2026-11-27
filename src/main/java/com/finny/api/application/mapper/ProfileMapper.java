package com.finny.api.application.mapper;

import com.finny.api.application.dto.request.OnboardingRequest;
import com.finny.api.application.dto.request.UpdateProfileRequest;
import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.domain.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = AvatarUrls.class)
public interface ProfileMapper {
    @Mapping(source = "user.fullName", target = "fullName")
    @Mapping(target = "avatarUrl", expression = "java(profile.getUser() == null ? null : AvatarUrls.forVersion(profile.getUser().getAvatarVersion()))")
    ProfileResponse toResponse(UserProfile profile);
    UserProfile toEntity(OnboardingRequest request);
    void updateEntityFromRequest(UpdateProfileRequest request, @MappingTarget UserProfile profile);
}
