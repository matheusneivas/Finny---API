package com.finny.api.application.mapper;

import com.finny.api.application.dto.request.GoalRequest;
import com.finny.api.application.dto.response.GoalResponse;
import com.finny.api.domain.entity.Goal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring", uses = {CategoryMapper.class})
public interface GoalMapper {

    @Mapping(target = "relatedCategory", source = "relatedCategory")
    @Mapping(target = "progressPercentage", expression = "java(calculateProgress(goal))")
    GoalResponse toResponse(Goal goal);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "relatedCategory", ignore = true)
    @Mapping(target = "savedAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    Goal toEntity(GoalRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "relatedCategory", ignore = true)
    @Mapping(target = "savedAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    void updateEntityFromRequest(GoalRequest request, @MappingTarget Goal goal);

    default BigDecimal calculateProgress(Goal goal) {
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return goal.getSavedAmount()
                .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
