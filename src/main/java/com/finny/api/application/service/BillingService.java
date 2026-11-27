package com.finny.api.application.service;

import com.finny.api.application.dto.response.BillingPlansResponse;
import com.finny.api.application.dto.response.PixChargeResponse;
import com.finny.api.application.dto.response.SubscriptionResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.EntityNotFoundException;
import com.finny.api.application.exception.ResourceConflictException;
import com.finny.api.application.exception.ServiceUnavailableException;
import com.finny.api.application.service.assistant.ChatProperties;
import com.finny.api.domain.entity.BillingEvent;
import com.finny.api.domain.entity.BillingTrial;
import com.finny.api.domain.entity.Subscription;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.SubscriptionStatus;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.BillingEventRepository;
import com.finny.api.domain.repository.BillingTrialRepository;
import com.finny.api.domain.repository.SubscriptionRepository;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.billing.BillingProperties;
import com.finny.api.infrastructure.billing.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Premium (plano único mensal). Três jeitos de ter Premium, todos somando em {@code users.plan_expires_at}:
 * <ul>
 *   <li>Teste grátis: uma vez por pessoa (CPF).</li>
 *   <li>Cartão: assinatura no Mercado Pago, cobrada todo mês. Cada cobrança paga libera até a data
 *       da cobrança + 1 mês + tolerância.</li>
 *   <li>Pix: pagamento avulso que soma {@code pixDays} dias ao Premium.</li>
 * </ul>
 * O gateway avisa pelo webhook; a API sempre confere o pagamento direto no gateway antes de liberar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    static final String PIX_REF_PREFIX = "finny-pix:";
    static final String CARD_REF_PREFIX = "finny-card:";

    private final PaymentGateway gateway;
    private final BillingProperties properties;
    private final ChatProperties chatProperties;
    private final PlanService planService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingTrialRepository trialRepository;
    private final BillingEventRepository eventRepository;

    public BillingPlansResponse plans() {
        return new BillingPlansResponse(
            gateway.enabled(),
            properties.getMonthlyPrice(),
            properties.getTrialDays(),
            properties.getPixDays(),
            new BillingPlansResponse.Free(planService.freeTransactionsPerMonth(), planService.freeActiveGoals(),
                chatProperties.getFreeMonthlyLimit()),
            chatProperties.getPremiumMonthlyLimit());
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse status(UUID userId) {
        return toResponse(findUser(userId));
    }

    /** Libera o Premium por {@code trialDays} dias, uma vez por pessoa. Não pede cartão. */
    @Transactional
    public SubscriptionResponse startTrial(UUID userId) {
        User user = findUser(userId);
        if (properties.getTrialDays() <= 0 || !trialAvailable(user)) {
            throw new ResourceConflictException("O teste grátis já foi usado.");
        }
        if (planService.isPremium(user)) {
            throw new ResourceConflictException("Você já tem o Premium.");
        }
        trialRepository.save(new BillingTrial(trialKey(user), userId, planService.now()));
        addPremium(user, planService.now().plusDays(properties.getTrialDays()));
        log.info("Teste grátis iniciado | user: {} | até {}", userId, user.getPlanExpiresAt());
        return toResponse(user);
    }

    /** Cria (ou devolve) a assinatura no cartão e o link do Mercado Pago para cadastrar o cartão. */
    @Transactional
    public SubscriptionResponse subscribeWithCard(UUID userId) {
        requireEnabled();
        User user = findUser(userId);
        Subscription sub = subscriptionRepository.findByUserId(userId).orElse(null);
        if (sub != null && sub.getStatus() == SubscriptionStatus.ACTIVE) {
            throw new ResourceConflictException("Você já assina o Premium no cartão.");
        }
        if (sub != null && sub.getStatus() == SubscriptionStatus.PENDING && sub.getCheckoutUrl() != null) {
            return toResponse(user); // pediu de novo antes de terminar: mesmo link
        }

        PaymentGateway.CardCheckout checkout = gateway.createCardSubscription(user.getEmail(),
            properties.getMonthlyPrice(), "Finny Premium", CARD_REF_PREFIX + userId, properties.getReturnUrl());
        if (sub == null) {
            sub = new Subscription();
            sub.setUserId(userId);
        }
        sub.setProvider(gateway.name());
        sub.setExternalId(checkout.id());
        sub.setStatus(SubscriptionStatus.PENDING);
        sub.setPrice(properties.getMonthlyPrice());
        sub.setCheckoutUrl(checkout.checkoutUrl());
        sub.setCanceledAt(null);
        subscriptionRepository.save(sub);
        log.info("Assinatura no cartão criada | user: {}", userId);
        return toResponse(user);
    }

    /** Gera um Pix que soma {@code pixDays} dias de Premium quando for pago. */
    @Transactional(readOnly = true)
    public PixChargeResponse payWithPix(UUID userId, String idempotencyKey) {
        requireEnabled();
        User user = findUser(userId);
        String key = idempotencyKey != null && idempotencyKey.matches("[A-Za-z0-9._:-]{8,64}")
            ? userId + ":" + idempotencyKey
            : UUID.randomUUID().toString();
        PaymentGateway.PixCharge pix = gateway.createPix(user.getEmail(), properties.getMonthlyPrice(),
            "Finny Premium (" + properties.getPixDays() + " dias)", PIX_REF_PREFIX + userId, key);
        log.info("Pix gerado | user: {} | pagamento: {}", userId, pix.id());
        return new PixChargeResponse(pix.id(), properties.getMonthlyPrice(), properties.getPixDays(), pix.qrCode(),
            pix.qrCodeBase64(), pix.ticketUrl(), pix.expiresAt());
    }

    /** Cancela a cobrança no cartão. O Premium já pago continua até vencer. */
    @Transactional
    public SubscriptionResponse cancelCard(UUID userId) {
        requireEnabled();
        User user = findUser(userId);
        Subscription sub = subscriptionRepository.findByUserId(userId)
            .filter(s -> s.getStatus() != SubscriptionStatus.CANCELED)
            .orElseThrow(() -> new BusinessException("Você não tem assinatura no cartão."));
        gateway.cancelCardSubscription(sub.getExternalId());
        markCanceled(sub);
        subscriptionRepository.save(sub);
        log.info("Assinatura no cartão cancelada pelo usuário | user: {}", userId);
        return toResponse(user);
    }

    /**
     * Aviso do gateway. Os tipos do Mercado Pago: "payment" (Pix), "subscription_preapproval"
     * (assinatura mudou) e "subscription_authorized_payment" (cobrança mensal do cartão).
     * Tipos desconhecidos são ignorados.
     */
    @Transactional
    public void handleNotification(String type, String resourceId) {
        if (type == null || resourceId == null || resourceId.isBlank()) return;
        switch (type) {
            case "payment" -> handlePixPayment(resourceId);
            case "subscription_preapproval" -> handleSubscriptionChange(resourceId);
            case "subscription_authorized_payment" -> handleCardCharge(resourceId);
            default -> log.info("Aviso do gateway ignorado | tipo: {}", type);
        }
    }

    // ── Avisos ──────────────────────────────────────────────────────────────

    private void handlePixPayment(String paymentId) {
        PaymentGateway.Payment payment = gateway.getPayment(paymentId);
        String ref = payment.externalReference();
        if (ref == null || !ref.startsWith(PIX_REF_PREFIX)) {
            return; // cobranças do cartão também geram "payment": essas entram por subscription_authorized_payment
        }
        User user = userFromRef(ref, PIX_REF_PREFIX);
        if (user == null) return;
        String eventKey = "pix:" + payment.id();

        if ("APPROVED".equals(payment.status())) {
            if (eventRepository.existsById(eventKey)) return;
            eventRepository.save(new BillingEvent(eventKey, "PIX_APPROVED", planService.now()));
            LocalDateTime base = currentPremiumEnd(user);
            addPremium(user, base.plusDays(properties.getPixDays()));
            log.info("Pix confirmado | user: {} | Premium até {}", user.getId(), user.getPlanExpiresAt());
        } else if ("REVERSED".equals(payment.status()) && eventRepository.existsById(eventKey)
                && !eventRepository.existsById(eventKey + ":reversed")) {
            eventRepository.save(new BillingEvent(eventKey + ":reversed", "PIX_REVERSED", planService.now()));
            removePremiumDays(user, properties.getPixDays());
            log.warn("Pix estornado | user: {}", user.getId());
        }
    }

    private void handleCardCharge(String chargeId) {
        PaymentGateway.CardCharge charge = gateway.getCardCharge(chargeId);
        if (!charge.paid()) return;
        String eventKey = "card:" + charge.id();
        if (eventRepository.existsById(eventKey)) return;
        Subscription sub = subscriptionRepository.findByExternalId(charge.subscriptionId()).orElse(null);
        if (sub == null) {
            log.warn("Cobrança de assinatura desconhecida {}", charge.subscriptionId());
            return;
        }
        eventRepository.save(new BillingEvent(eventKey, "CARD_PAID", planService.now()));
        User user = findUser(sub.getUserId());
        LocalDateTime until = charge.chargeDate().plusMonths(1).plusDays(properties.getGraceDays() + 1L).atStartOfDay();
        // Se a pessoa tinha dias de Pix ou de teste sobrando, eles não se perdem.
        LocalDateTime base = currentPremiumEnd(user);
        if (base.isAfter(charge.chargeDate().atStartOfDay())) {
            until = until.isAfter(base.plusMonths(1)) ? until : base.plusMonths(1);
        }
        addPremium(user, until);
        if (sub.getStatus() == SubscriptionStatus.PENDING) {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setCheckoutUrl(null);
            subscriptionRepository.save(sub);
        }
        log.info("Cobrança do cartão paga | user: {} | Premium até {}", user.getId(), user.getPlanExpiresAt());
    }

    private void handleSubscriptionChange(String subscriptionId) {
        Subscription sub = subscriptionRepository.findByExternalId(subscriptionId).orElse(null);
        if (sub == null) return;
        PaymentGateway.CardSubscription remote = gateway.getCardSubscription(subscriptionId);
        switch (remote.status()) {
            case "ACTIVE" -> {
                sub.setStatus(SubscriptionStatus.ACTIVE);
                sub.setCheckoutUrl(null);
            }
            case "CANCELED" -> markCanceled(sub);
            default -> { }
        }
        subscriptionRepository.save(sub);
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────

    /** Fim do Premium atual, ou agora se não tiver Premium. */
    private LocalDateTime currentPremiumEnd(User user) {
        LocalDateTime now = planService.now();
        LocalDateTime end = user.getPlan() == UserPlan.PREMIUM ? user.getPlanExpiresAt() : null;
        return end != null && end.isAfter(now) ? end : now;
    }

    private void addPremium(User user, LocalDateTime until) {
        if (user.getPlan() == UserPlan.PREMIUM && user.getPlanExpiresAt() == null) return; // Premium sem prazo
        LocalDateTime current = user.getPlan() == UserPlan.PREMIUM ? user.getPlanExpiresAt() : null;
        if (current == null || until.isAfter(current)) user.setPlanExpiresAt(until);
        user.setPlan(UserPlan.PREMIUM);
        userRepository.save(user);
    }

    private void removePremiumDays(User user, int days) {
        if (user.getPlan() != UserPlan.PREMIUM || user.getPlanExpiresAt() == null) return;
        user.setPlanExpiresAt(user.getPlanExpiresAt().minusDays(days));
        userRepository.save(user);
    }

    private void markCanceled(Subscription sub) {
        if (sub.getStatus() == SubscriptionStatus.CANCELED) return;
        sub.setStatus(SubscriptionStatus.CANCELED);
        sub.setCanceledAt(planService.now());
        sub.setCheckoutUrl(null);
    }

    private boolean trialAvailable(User user) {
        return properties.getTrialDays() > 0
            && !trialRepository.existsById(trialKey(user))
            && !trialRepository.existsById("user:" + user.getId());
    }

    private static String trialKey(User user) {
        return user.getCpfHash() != null ? user.getCpfHash() : "user:" + user.getId();
    }

    private User userFromRef(String ref, String prefix) {
        try {
            return userRepository.findById(UUID.fromString(ref.substring(prefix.length()))).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("Referência de pagamento inválida: {}", ref);
            return null;
        }
    }

    private SubscriptionResponse toResponse(User user) {
        UserPlan plan = planService.effectivePlan(user);
        SubscriptionResponse.Card card = subscriptionRepository.findByUserId(user.getId())
            .map(s -> new SubscriptionResponse.Card(s.getStatus(),
                s.getStatus() == SubscriptionStatus.PENDING ? s.getCheckoutUrl() : null))
            .orElse(null);
        return new SubscriptionResponse(
            plan,
            plan == UserPlan.PREMIUM ? user.getPlanExpiresAt() : null,
            trialAvailable(user) && plan == UserPlan.FREE,
            card,
            new SubscriptionResponse.Usage(planService.transactionsThisMonth(user.getId()),
                planService.activeGoals(user.getId())));
    }

    private void requireEnabled() {
        if (!gateway.enabled()) {
            throw new ServiceUnavailableException("As assinaturas ainda não estão disponíveis.");
        }
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));
    }
}
