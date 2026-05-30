package com.finny.api.application.mapper;

import com.finny.api.application.dto.response.UserResponse;
import com.finny.api.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(source = "fullName", target = "name")
    @Mapping(source = "profile.onboardingCompleted", target = "onboardingCompleted", defaultExpression = "java(Boolean.FALSE)")
    UserResponse toResponse(User user);
}
