package com.finny.api.application.mapper;

import com.finny.api.application.dto.response.UserResponse;
import com.finny.api.domain.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
