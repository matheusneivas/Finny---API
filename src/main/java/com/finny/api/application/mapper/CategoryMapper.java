package com.finny.api.application.mapper;

import com.finny.api.application.dto.request.CategoryRequest;
import com.finny.api.application.dto.response.CategoryResponse;
import com.finny.api.domain.entity.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryResponse toResponse(Category category);
    Category toEntity(CategoryRequest request);
    void updateEntityFromRequest(CategoryRequest request, @MappingTarget Category category);
}
