package com.finny.api.application.service;

import com.finny.api.application.dto.request.OnboardingRequest;
import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.mapper.ProfileMapper;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.entity.UserProfile;
import com.finny.api.domain.repository.UserProfileRepository;
import com.finny.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileMapper profileMapper;

    @Transactional
    public ProfileResponse completeOnboarding(UUID userId, OnboardingRequest request) {
        log.debug("Completing onboarding for user: {}", userId);

        // Validar idade
        if (!request.isAdult()) {
            throw new BusinessException("Usuário deve ter pelo menos 18 anos");
        }

        // Buscar usuário
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        // Verificar se já completou onboarding
        if (user.getProfile() != null && user.getProfile().getOnboardingCompleted()) {
            throw new BusinessException("Onboarding já foi completado");
        }

        // Criar ou atualizar perfil
        UserProfile profile;
        if (user.getProfile() != null) {
            profile = user.getProfile();
        } else {
            profile = new UserProfile();
            profile.setUser(user);
            profile.setUserId(userId);
        }

        // Mapear dados do request para o profile
        profile.setBirthDate(request.getBirthDate());
        profile.setOccupation(request.getOccupation());
        profile.setIncomeRange(request.getIncomeRange());
        profile.setHasDebts(request.getHasDebts());
        profile.setTotalDebt(request.getTotalDebt());
        profile.setDebtTypes(request.getDebtTypes());
        profile.setHasEmergencyFund(request.getHasEmergencyFund());
        profile.setEmergencyFundMonths(request.getEmergencyFundMonths());
        profile.setHasInvestments(request.getHasInvestments());
        profile.setInvestmentTypes(request.getInvestmentTypes());
        profile.setShortTermGoals(request.getShortTermGoals());
        profile.setLongTermGoals(request.getLongTermGoals());
        profile.setPrimaryGoal(request.getPrimaryGoal());
        profile.setPrimaryGoalTimeframe(request.getPrimaryGoalTimeframe());
        profile.setExpenseControlPreference(request.getExpenseControlPreference());
        profile.setMainExpenseCategories(request.getMainExpenseCategories());
        profile.setKnowledgeLevel(request.getKnowledgeLevel());
        profile.setReportFrequency(request.getReportFrequency());
        profile.setCommunicationStyle(request.getCommunicationStyle());
        profile.setNotificationPreferences(request.getNotificationPreferences());
        profile.setOnboardingCompleted(true);
        profile.setOnboardingCompletedAt(LocalDateTime.now());

        profile = userProfileRepository.save(profile);

        log.info("Onboarding completed successfully for user: {}", userId);

        return profileMapper.toResponse(profile);
    }
}
