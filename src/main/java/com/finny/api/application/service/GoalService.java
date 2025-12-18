package com.finny.api.application.service;

import com.finny.api.application.dto.request.GoalContributionRequest;
import com.finny.api.application.dto.request.GoalRequest;
import com.finny.api.application.dto.response.GoalProgressResponse;
import com.finny.api.application.dto.response.GoalResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.GoalMapper;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.Goal;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.GoalStatus;
import com.finny.api.domain.repository.CategoryRepository;
import com.finny.api.domain.repository.GoalRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final GoalMapper goalMapper;

    @Transactional(readOnly = true)
    public List<GoalResponse> getGoals(UUID userId) {
        log.debug("Getting goals for user: {}", userId);

        List<Goal> goals = goalRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return goals.stream()
                .map(goalMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoalById(UUID userId, UUID goalId) {
        log.debug("Getting goal: {} for user: {}", goalId, userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

        return goalMapper.toResponse(goal);
    }

    @Transactional
    public GoalResponse createGoal(UUID userId, GoalRequest request) {
        log.debug("Creating goal for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        Goal goal = goalMapper.toEntity(request);
        goal.setUser(user);
        goal.setSavedAmount(BigDecimal.ZERO);
        goal.setStatus(GoalStatus.ACTIVE);

        // Se tiver categoria relacionada
        if (request.getRelatedCategoryId() != null) {
            Category category = categoryRepository.findById(request.getRelatedCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

            // Validar se categoria pertence ao usuário ou é padrão
            if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
                throw new BusinessException("Categoria não pertence ao usuário");
            }

            goal.setRelatedCategory(category);
        }

        goal = goalRepository.save(goal);

        log.info("Goal created successfully: {}", goal.getId());

        return goalMapper.toResponse(goal);
    }

    @Transactional
    public GoalResponse updateGoal(UUID userId, UUID goalId, GoalRequest request) {
        log.debug("Updating goal: {} for user: {}", goalId, userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

        goalMapper.updateEntityFromRequest(request, goal);

        // Se tiver categoria relacionada
        if (request.getRelatedCategoryId() != null) {
            Category category = categoryRepository.findById(request.getRelatedCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Categoria não encontrada"));

            // Validar se categoria pertence ao usuário ou é padrão
            if (category.getUser() != null && !category.getUser().getId().equals(userId)) {
                throw new BusinessException("Categoria não pertence ao usuário");
            }

            goal.setRelatedCategory(category);
        } else {
            goal.setRelatedCategory(null);
        }

        goal = goalRepository.save(goal);

        log.info("Goal updated successfully: {}", goalId);

        return goalMapper.toResponse(goal);
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
        log.debug("Deleting goal: {} for user: {}", goalId, userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

        goalRepository.delete(goal);

        log.info("Goal deleted successfully: {}", goalId);
    }

    @Transactional
    public GoalProgressResponse contributeToGoal(UUID userId, UUID goalId, GoalContributionRequest request) {
        log.debug("Contributing to goal: {} for user: {}", goalId, userId);

        Goal goal = goalRepository.findByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Meta não encontrada"));

        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw new BusinessException("Meta não está ativa");
        }

        // Adicionar contribuição
        BigDecimal newSavedAmount = goal.getSavedAmount().add(request.getAmount());
        goal.setSavedAmount(newSavedAmount);

        // Verificar se atingiu a meta
        if (newSavedAmount.compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
            goal.setCompletedAt(LocalDateTime.now());
        }

        goal = goalRepository.save(goal);

        // Calcular progresso
        BigDecimal progressPercentage = goal.getSavedAmount()
                .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        String message = goal.getStatus() == GoalStatus.COMPLETED
                ? "Parabéns! Você atingiu sua meta!"
                : String.format("Contribuição registrada com sucesso! Faltam R$ %.2f para atingir sua meta.",
                        goal.getTargetAmount().subtract(goal.getSavedAmount()));

        log.info("Goal contribution successful: {}", goalId);

        return GoalProgressResponse.builder()
                .savedAmount(goal.getSavedAmount())
                .progressPercentage(progressPercentage)
                .message(message)
                .build();
    }
}
