package com.finny.api.application.service.assistant;

import com.finny.api.domain.entity.ChatUsage;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.ChatUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Limite mensal de mensagens do chat.
 * <ul>
 *   <li>Conta apenas as mensagens enviadas pelo usuário (respostas da IA não contam).</li>
 *   <li>Zera todo mês (fuso America/Sao_Paulo por padrão).</li>
 *   <li>Contador separado do histórico: apagar a conversa não devolve mensagens.</li>
 *   <li>Reserva atômica no banco: requisições simultâneas não furam o limite.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatUsageService {

    private final ChatUsageRepository chatUsageRepository;
    private final ChatProperties chatProperties;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public record MonthlyUsage(long used, long limit, ZonedDateTime resetsAt) {
        public boolean exceeded() {
            return used >= limit;
        }

        public long remaining() {
            return Math.max(0, limit - used);
        }
    }

    public MonthlyUsage getMonthlyUsage(UUID userId, UserPlan plan) {
        YearMonth month = currentMonth();
        long used = chatUsageRepository.findByUserIdAndPeriod(userId, month.toString())
            .map(ChatUsage::getMessageCount)
            .orElse(0);
        return new MonthlyUsage(used, limitFor(plan), resetsAt(month));
    }

    /**
     * Reserva uma mensagem do mês. Retorna o uso já contando esta mensagem,
     * ou {@code null} quando o limite foi atingido.
     */
    public MonthlyUsage tryReserve(UUID userId, UserPlan plan) {
        YearMonth month = currentMonth();
        String period = month.toString();
        long limit = limitFor(plan);

        ensureRow(userId, period);
        int updated = newTransaction().execute(s -> chatUsageRepository.incrementIfBelow(userId, period, limit));
        if (updated == 0) {
            return null;
        }
        return getMonthlyUsage(userId, plan);
    }

    /** Devolve a mensagem reservada (ex.: a IA falhou e o usuário não recebeu resposta). */
    public void release(UUID userId) {
        String period = currentMonth().toString();
        newTransaction().executeWithoutResult(s -> chatUsageRepository.decrement(userId, period));
    }

    public long limitFor(UserPlan plan) {
        return plan == UserPlan.PREMIUM
            ? chatProperties.getPremiumMonthlyLimit()
            : chatProperties.getFreeMonthlyLimit();
    }

    private void ensureRow(UUID userId, String period) {
        if (chatUsageRepository.findByUserIdAndPeriod(userId, period).isPresent()) {
            return;
        }
        try {
            newTransaction().executeWithoutResult(s ->
                chatUsageRepository.saveAndFlush(new ChatUsage(null, userId, period, 0)));
        } catch (DataIntegrityViolationException alreadyCreated) {
            // outra requisição criou a linha ao mesmo tempo: segue o fluxo
        }
    }

    private YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(zone()));
    }

    private ZonedDateTime resetsAt(YearMonth month) {
        return month.plusMonths(1).atDay(1).atStartOfDay(zone());
    }

    private ZoneId zone() {
        return chatProperties.getTimezone();
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
