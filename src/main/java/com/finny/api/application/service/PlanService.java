package com.finny.api.application.service;

import com.finny.api.application.exception.PlanLimitException;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.GoalStatus;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.GoalRepository;
import com.finny.api.domain.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Plano que vale agora e limites do grátis. O Premium termina sozinho quando {@code planExpiresAt}
 * passa (sem tarefa agendada). {@code planExpiresAt} nulo com plano PREMIUM = Premium sem prazo (dado à mão).
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private final Clock clock;
    private final PlanLimitsProperties limits;
    private final TransactionRepository transactionRepository;
    private final GoalRepository goalRepository;

    public UserPlan effectivePlan(User user) {
        if (user.getPlan() != UserPlan.PREMIUM) return UserPlan.FREE;
        LocalDateTime expires = user.getPlanExpiresAt();
        return expires == null || expires.isAfter(now()) ? UserPlan.PREMIUM : UserPlan.FREE;
    }

    public boolean isPremium(User user) {
        return effectivePlan(user) == UserPlan.PREMIUM;
    }

    /** Lançamentos criados neste mês (conta também os apagados, para apagar e criar de novo não driblar o limite). */
    public long transactionsThisMonth(UUID userId) {
        LocalDateTime monthStart = now().toLocalDate().withDayOfMonth(1).atStartOfDay();
        return transactionRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, monthStart);
    }

    public long activeGoals(UUID userId) {
        return goalRepository.countByUserIdAndStatus(userId, GoalStatus.ACTIVE);
    }

    /** @param toCreate quantos lançamentos o pedido vai criar (repetições contam). */
    public void checkTransactionQuota(User user, long toCreate) {
        if (isPremium(user)) return;
        long limit = limits.getFreeTransactionsPerMonth();
        long used = transactionsThisMonth(user.getId());
        if (used + toCreate > limit) {
            long left = Math.max(0, limit - used);
            throw new PlanLimitException(left == 0
                ? String.format("Você usou os %d lançamentos do plano grátis deste mês. Assine o Premium para lançar sem limite.", limit)
                : String.format("O plano grátis permite %d lançamentos por mês e restam %d. Assine o Premium para lançar sem limite.", limit, left));
        }
    }

    public void checkGoalQuota(User user) {
        if (isPremium(user)) return;
        long limit = limits.getFreeActiveGoals();
        if (activeGoals(user.getId()) >= limit) {
            throw new PlanLimitException(limit == 1
                ? "O plano grátis permite 1 meta ativa. Assine o Premium para ter metas sem limite."
                : String.format("O plano grátis permite %d metas ativas. Assine o Premium para ter metas sem limite.", limit));
        }
    }

    public long freeTransactionsPerMonth() {
        return limits.getFreeTransactionsPerMonth();
    }

    public long freeActiveGoals() {
        return limits.getFreeActiveGoals();
    }

    /** Mesmo fuso dos outros horários gravados (created_at etc.): o da JVM (São Paulo no Render). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
