package com.finny.api.application.service;

import com.finny.api.application.dto.request.*;
import com.finny.api.application.dto.response.OnboardingStepResponse;
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

    private static final int TOTAL_STEPS = 5;

    private static final String[] STEP_DESCRIPTIONS = {
            "Informações básicas (data de nascimento e ocupação)",
            "Renda e moradia (faixa de renda e aluguel)",
            "Situação financeira (dívidas e reserva de emergência)",
            "Investimentos (perfil e meta mensal)",
            "Objetivos e preferências"
    };

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileMapper profileMapper;

    // ── GET status ──────────────────────────────────────────────────────────

    public OnboardingStepResponse getStatus(UUID userId) {
        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        return buildStepResponse(profile);
    }

    // ── STEP 1: Informações básicas ─────────────────────────────────────────

    @Transactional
    public OnboardingStepResponse saveStep1(UUID userId, OnboardingStep1Request request) {
        if (!request.isAdult()) {
            throw new BusinessException("Usuário deve ter pelo menos 18 anos");
        }

        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        assertNotCompleted(profile);

        profile.setBirthDate(request.getBirthDate());
        profile.setOccupation(request.getOccupation());
        profile.setCurrentStep(Math.max(profile.getCurrentStep(), 1));

        userProfileRepository.save(profile);
        log.debug("Onboarding step 1 saved for user: {}", userId);
        return buildStepResponse(profile);
    }

    // ── STEP 2: Renda e moradia ─────────────────────────────────────────────

    @Transactional
    public OnboardingStepResponse saveStep2(UUID userId, OnboardingStep2Request request) {
        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        assertNotCompleted(profile);
        assertStepReached(profile, 1);

        profile.setIncomeRange(request.getIncomeRange());
        profile.setPaysRent(request.getPaysRent());

        if (Boolean.TRUE.equals(request.getPaysRent())) {
            if (request.getMonthlyRent() == null) {
                throw new BusinessException("Informe o valor do aluguel");
            }
            profile.setMonthlyRent(request.getMonthlyRent());
        } else {
            profile.setMonthlyRent(null);
        }

        profile.setCurrentStep(Math.max(profile.getCurrentStep(), 2));

        userProfileRepository.save(profile);
        log.debug("Onboarding step 2 saved for user: {}", userId);
        return buildStepResponse(profile);
    }

    // ── STEP 3: Dívidas e reserva de emergência ─────────────────────────────

    @Transactional
    public OnboardingStepResponse saveStep3(UUID userId, OnboardingStep3Request request) {
        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        assertNotCompleted(profile);
        assertStepReached(profile, 2);

        profile.setHasDebts(request.getHasDebts());

        if (Boolean.TRUE.equals(request.getHasDebts())) {
            profile.setTotalDebt(request.getTotalDebt());
            profile.setDebtTypes(request.getDebtTypes());
        } else {
            profile.setTotalDebt(null);
            profile.setDebtTypes(null);
        }

        profile.setHasEmergencyFund(request.getHasEmergencyFund());

        if (Boolean.TRUE.equals(request.getHasEmergencyFund())) {
            profile.setEmergencyFundMonths(request.getEmergencyFundMonths());
        } else {
            profile.setEmergencyFundMonths(null);
        }

        profile.setCurrentStep(Math.max(profile.getCurrentStep(), 3));

        userProfileRepository.save(profile);
        log.debug("Onboarding step 3 saved for user: {}", userId);
        return buildStepResponse(profile);
    }

    // ── STEP 4: Investimentos ───────────────────────────────────────────────

    @Transactional
    public OnboardingStepResponse saveStep4(UUID userId, OnboardingStep4Request request) {
        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        assertNotCompleted(profile);
        assertStepReached(profile, 3);

        profile.setHasInvestments(request.getHasInvestments());

        if (Boolean.TRUE.equals(request.getHasInvestments())) {
            profile.setInvestmentTypes(request.getInvestmentTypes());
        } else {
            profile.setInvestmentTypes(null);
        }

        profile.setMonthlyInvestmentGoal(request.getMonthlyInvestmentGoal());
        profile.setCurrentStep(Math.max(profile.getCurrentStep(), 4));

        userProfileRepository.save(profile);
        log.debug("Onboarding step 4 saved for user: {}", userId);
        return buildStepResponse(profile);
    }

    // ── STEP 5: Objetivos e preferências ───────────────────────────────────

    @Transactional
    public OnboardingStepResponse saveStep5(UUID userId, OnboardingStep5Request request) {
        User user = findUser(userId);
        UserProfile profile = getOrCreateProfile(user);
        assertNotCompleted(profile);
        assertStepReached(profile, 4);

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

        profile.setCurrentStep(TOTAL_STEPS);
        profile.setOnboardingCompleted(true);
        profile.setOnboardingCompletedAt(LocalDateTime.now());

        userProfileRepository.save(profile);
        log.info("Onboarding completed for user: {}", userId);
        return buildStepResponse(profile);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }

    private UserProfile getOrCreateProfile(User user) {
        if (user.getProfile() != null) {
            return user.getProfile();
        }
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setUserId(user.getId());
        profile.setCurrentStep(0);
        profile.setOnboardingCompleted(false);
        return userProfileRepository.save(profile);
    }

    private void assertNotCompleted(UserProfile profile) {
        if (Boolean.TRUE.equals(profile.getOnboardingCompleted())) {
            throw new BusinessException("Onboarding já foi completado");
        }
    }

    private void assertStepReached(UserProfile profile, int requiredStep) {
        if (profile.getCurrentStep() < requiredStep) {
            throw new BusinessException(
                    "Complete o passo " + requiredStep + " antes de continuar. " +
                    "Passo atual: " + profile.getCurrentStep()
            );
        }
    }

    private OnboardingStepResponse buildStepResponse(UserProfile profile) {
        int current = profile.getCurrentStep();
        boolean completed = Boolean.TRUE.equals(profile.getOnboardingCompleted());
        int progress = (int) Math.round((current / (double) TOTAL_STEPS) * 100);

        String nextDescription = completed
                ? "Onboarding concluído!"
                : STEP_DESCRIPTIONS[current]; // próximo step a completar

        ProfileResponse profileResponse = profileMapper.toResponse(profile);

        return OnboardingStepResponse.builder()
                .currentStep(current)
                .totalSteps(TOTAL_STEPS)
                .progressPercentage(progress)
                .completed(completed)
                .nextStepDescription(nextDescription)
                .profile(profileResponse)
                .build();
    }
}
