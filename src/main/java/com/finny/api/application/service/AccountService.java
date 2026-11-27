package com.finny.api.application.service;

import com.finny.api.application.dto.response.ProfileResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.ServiceUnavailableException;
import com.finny.api.application.exception.UnauthorizedException;
import com.finny.api.domain.entity.ChatMessage;
import com.finny.api.domain.entity.Goal;
import com.finny.api.domain.entity.Subscription;
import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.SubscriptionStatus;
import com.finny.api.domain.repository.SubscriptionRepository;
import com.finny.api.domain.repository.UserProfileRepository;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.billing.PaymentGateway;
import com.finny.api.application.mapper.ProfileMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Direitos do titular (LGPD, art. 18): baixar uma cópia dos próprios dados e excluir a conta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ProfileMapper profileMapper;
    private final PasswordEncoder passwordEncoder;
    private final PaymentGateway gateway;
    private final EntityManager em;

    /** Tudo o que o Finny guarda sobre a pessoa, num JSON legível. */
    @Transactional(readOnly = true)
    public Map<String, Object> exportData(UUID userId) {
        User user = findUser(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("geradoEm", OffsetDateTime.now().toString());
        out.put("aviso", "Cópia dos seus dados no Finny. O CPF não aparece porque guardamos só um código "
            + "irreversível dele (não temos o número). A senha também é guardada só como código irreversível.");

        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("id", user.getId());
        conta.put("nome", user.getFullName());
        conta.put("email", user.getEmail());
        conta.put("emailVerificado", user.getEmailVerified());
        conta.put("celular", user.getPhone());
        conta.put("plano", user.getPlan());
        conta.put("premiumAte", user.getPlanExpiresAt());
        conta.put("termosAceitosEm", user.getTermsAcceptedAt());
        conta.put("versaoDosTermos", user.getTermsVersion());
        conta.put("criadaEm", user.getCreatedAt());
        conta.put("temFotoDePerfil", user.getAvatarVersion() != null);
        out.put("conta", conta);

        ProfileResponse profile = userProfileRepository.findById(userId).map(profileMapper::toResponse).orElse(null);
        out.put("perfilFinanceiro", profile);

        List<Transaction> transactions = em.createQuery(
                "select t from Transaction t join fetch t.category where t.user.id = :u order by t.transactionDate",
                Transaction.class)
            .setParameter("u", userId).getResultList();
        out.put("lancamentos", transactions.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("data", t.getTransactionDate());
            m.put("tipo", t.getType());
            m.put("valor", t.getAmount());
            m.put("descricao", t.getDescription());
            m.put("categoria", t.getCategory().getName());
            m.put("observacoes", t.getNotes());
            m.put("frequencia", t.getFrequency());
            m.put("origem", t.getSource());
            m.put("excluido", t.getDeleted());
            m.put("criadoEm", t.getCreatedAt());
            return m;
        }).toList());

        List<Goal> goals = em.createQuery("select g from Goal g where g.user.id = :u order by g.createdAt", Goal.class)
            .setParameter("u", userId).getResultList();
        out.put("metas", goals.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("nome", g.getName());
            m.put("valorAlvo", g.getTargetAmount());
            m.put("valorGuardado", g.getSavedAmount());
            m.put("prazo", g.getDeadline());
            m.put("status", g.getStatus());
            m.put("criadaEm", g.getCreatedAt());
            m.put("concluidaEm", g.getCompletedAt());
            return m;
        }).toList());

        out.put("categoriasCriadas", em.createQuery(
                "select c.name from Category c where c.user.id = :u order by c.name", String.class)
            .setParameter("u", userId).getResultList());

        List<ChatMessage> messages = em.createQuery(
                "select m from ChatMessage m where m.user.id = :u order by m.createdAt", ChatMessage.class)
            .setParameter("u", userId).getResultList();
        out.put("conversasComAIA", messages.stream().map(msg -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quem", msg.getRole());
            m.put("texto", msg.getContent());
            m.put("em", msg.getCreatedAt());
            return m;
        }).toList());

        out.put("assinatura", subscriptionRepository.findByUserId(userId).map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("formaDePagamento", s.getProvider());
            m.put("status", s.getStatus());
            m.put("valor", s.getPrice());
            m.put("criadaEm", s.getCreatedAt());
            m.put("canceladaEm", s.getCanceledAt());
            return m;
        }).orElse(null));

        log.info("Dados exportados pelo titular | user: {}", userId);
        return out;
    }

    /**
     * Apaga a conta e todos os dados ligados a ela, de forma definitiva.
     * Fica guardado só o registro de que aquele CPF já usou o teste grátis (código irreversível,
     * sem ligação com a conta), para impedir testes grátis repetidos.
     */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User user = findUser(userId);
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("Senha incorreta");
        }

        // Para de cobrar no cartão antes de apagar (senão a cobrança continuaria sem conta)
        subscriptionRepository.findByUserId(userId)
            .filter(s -> s.getStatus() != SubscriptionStatus.CANCELED && s.getExternalId() != null)
            .ifPresent(this::cancelAtGateway);

        String[] byUserId = {
            "delete from ChatAction a where a.userId = :u",
            "delete from ChatMessage m where m.user.id = :u",
            "delete from ChatUsage c where c.userId = :u",
            "delete from IdempotencyRecord i where i.userId = :u",
            "delete from EmailCode e where e.userId = :u",
            "delete from RefreshToken r where r.userId = :u",
            "delete from UserDeviceToken d where d.user.id = :u",
            "delete from OcrProcessingQueue o where o.user.id = :u",
            "delete from Goal g where g.user.id = :u",
            "delete from Transaction t where t.user.id = :u",
            "delete from Category c where c.user.id = :u",
            "delete from Subscription s where s.userId = :u",
            "delete from UserAvatar a where a.userId = :u",
        };
        for (String jpql : byUserId) {
            em.createQuery(jpql).setParameter("u", userId).executeUpdate();
        }
        // Teste grátis: mantém a chave (HMAC do CPF), mas solta da conta apagada
        em.createQuery("update BillingTrial b set b.userId = :zero where b.userId = :u")
            .setParameter("zero", new UUID(0, 0)).setParameter("u", userId).executeUpdate();

        userRepository.delete(user); // perfil e listas do perfil vão junto (cascade)
        userRepository.flush();
        log.info("Conta excluída pelo titular | user: {}", userId);
    }

    private void cancelAtGateway(Subscription sub) {
        if (!gateway.enabled()) {
            throw new ServiceUnavailableException(
                "Não conseguimos cancelar sua assinatura agora. Tente de novo mais tarde.");
        }
        try {
            gateway.cancelCardSubscription(sub.getExternalId());
        } catch (RuntimeException e) {
            log.error("Falha ao cancelar assinatura antes de excluir a conta | user: {}", sub.getUserId());
            throw new ServiceUnavailableException(
                "Não conseguimos cancelar sua assinatura agora. Tente de novo em alguns minutos.");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
    }
}
