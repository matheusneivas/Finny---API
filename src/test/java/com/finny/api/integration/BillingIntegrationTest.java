package com.finny.api.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.finny.api.domain.entity.Category;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.TransactionType;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.billing.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    PaymentGateway gateway;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void gatewayOn() {
        when(gateway.enabled()).thenReturn(true);
        when(gateway.name()).thenReturn("mercadopago");
        when(gateway.verifyWebhook(any(), any(), any())).thenReturn(true);
    }

    // ── Cadastro ────────────────────────────────────────────────────────────

    @Test
    void registerRequiresValidUniqueCpfAndPhone() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("email", "a-" + System.nanoTime() + "@example.com",
            "password", "Senha123", "fullName", "Fulana de Tal", "phone", "11987654321"));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.cpf").exists());

        body.put("cpf", "123.456.789-00");
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
            .andExpect(status().isUnprocessableEntity());

        String cpf = randomCpf();
        body.put("cpf", cpf);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.phone").value("11987654321"));

        body.put("email", "b-" + System.nanoTime() + "@example.com");
        body.put("cpf", cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9));
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
            .andExpect(status().isConflict());
    }

    // ── Limites do grátis ───────────────────────────────────────────────────

    @Test
    void freePlanLimitsTransactionsAndGoals() throws Exception {
        Session s = register();
        Category food = defaultCategory("Alimentação", TransactionType.EXPENSE);

        createTransaction(s, food, 14).andExpect(status().isCreated());
        createTransaction(s, food, 2).andExpect(status().isPaymentRequired())
            .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        createTransaction(s, food, 1).andExpect(status().isCreated());
        createTransaction(s, food, 1).andExpect(status().isPaymentRequired());

        createGoal(s).andExpect(status().isCreated());
        createGoal(s).andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));

        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan").value("FREE"))
            .andExpect(jsonPath("$.usage.transactionsThisMonth").value(15))
            .andExpect(jsonPath("$.usage.activeGoals").value(1));
    }

    @Test
    void trialUnlocksPremiumOnceAndExpiredPremiumFallsBackToFree() throws Exception {
        Session s = register();
        mockMvc.perform(post("/api/v1/billing/trial").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plan").value("PREMIUM"))
            .andExpect(jsonPath("$.trialAvailable").value(false));
        createGoal(s).andExpect(status().isCreated());
        createGoal(s).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/billing/trial").header("Authorization", bearer(s)))
            .andExpect(status().isConflict());

        User user = userRepository.findById(s.userId()).orElseThrow();
        user.setPlanExpiresAt(LocalDateTime.now().minusMinutes(1));
        userRepository.save(user);
        mockMvc.perform(get("/api/v1/billing/subscription").header("Authorization", bearer(s)))
            .andExpect(jsonPath("$.plan").value("FREE"))
            .andExpect(jsonPath("$.premiumUntil").doesNotExist());
        createGoal(s).andExpect(status().isPaymentRequired());
    }

    // ── Pix ─────────────────────────────────────────────────────────────────

    @Test
    void approvedPixAddsThirtyDaysOnlyOnce() throws Exception {
        Session s = register();
        when(gateway.createPix(anyString(), any(), anyString(), anyString(), anyString())).thenReturn(
            new PaymentGateway.PixCharge("123", "000201...", "iVBOR", "https://mp/ticket", OffsetDateTime.now().plusDays(1)));
        mockMvc.perform(post("/api/v1/billing/pix").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.qrCode").value("000201..."))
            .andExpect(jsonPath("$.amount").value(9.90));

        when(gateway.getPayment("123")).thenReturn(new PaymentGateway.Payment("123", "APPROVED", "finny-pix:" + s.userId()));
        webhook("payment", "123").andExpect(status().isOk());
        webhook("payment", "123").andExpect(status().isOk());

        User user = userRepository.findById(s.userId()).orElseThrow();
        assertThat(user.getPlan()).isEqualTo(UserPlan.PREMIUM);
        assertThat(user.getPlanExpiresAt()).isBetween(LocalDateTime.now().plusDays(29), LocalDateTime.now().plusDays(31));
    }

    @Test
    void webhookWithBadSignatureIsRejected() throws Exception {
        when(gateway.verifyWebhook(any(), any(), any())).thenReturn(false);
        webhook("payment", "999").andExpect(status().isUnauthorized());
    }

    @Test
    void paymentOfAnotherKindIsIgnored() throws Exception {
        Session s = register();
        when(gateway.getPayment("555")).thenReturn(new PaymentGateway.Payment("555", "APPROVED", "outra-coisa"));
        webhook("payment", "555").andExpect(status().isOk());
        assertThat(userRepository.findById(s.userId()).orElseThrow().getPlan()).isEqualTo(UserPlan.FREE);
    }

    // ── Cartão ──────────────────────────────────────────────────────────────

    @Test
    void cardSubscriptionBecomesActiveWhenChargeIsPaid() throws Exception {
        Session s = register();
        when(gateway.createCardSubscription(anyString(), any(), anyString(), anyString(), any()))
            .thenReturn(new PaymentGateway.CardCheckout("pre-1", "https://mp/checkout"));
        mockMvc.perform(post("/api/v1/billing/card").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.status").value("PENDING"))
            .andExpect(jsonPath("$.card.checkoutUrl").value("https://mp/checkout"));

        when(gateway.getCardCharge("ap-1")).thenReturn(new PaymentGateway.CardCharge("ap-1", "pre-1", true, LocalDate.now()));
        webhook("subscription_authorized_payment", "ap-1").andExpect(status().isOk());

        JsonNode status = objectMapper.readTree(mockMvc.perform(get("/api/v1/billing/subscription")
            .header("Authorization", bearer(s))).andReturn().getResponse().getContentAsString());
        assertThat(status.path("plan").asText()).isEqualTo("PREMIUM");
        assertThat(status.path("card").path("status").asText()).isEqualTo("ACTIVE");

        mockMvc.perform(post("/api/v1/billing/card/cancel").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.status").value("CANCELED"))
            .andExpect(jsonPath("$.plan").value("PREMIUM")); // o mês pago continua valendo
    }

    @Test
    void billingRoutesAnswer503WhenPaymentsAreOff() throws Exception {
        when(gateway.enabled()).thenReturn(false);
        Session s = register();
        mockMvc.perform(get("/api/v1/billing/plans").header("Authorization", bearer(s)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.monthlyPrice").value(9.90))
            .andExpect(jsonPath("$.free.transactionsPerMonth").value(15));
        mockMvc.perform(post("/api/v1/billing/card").header("Authorization", bearer(s)))
            .andExpect(status().isServiceUnavailable());
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────

    private ResultActions createTransaction(Session s, Category category, int count) throws Exception {
        return mockMvc.perform(post("/api/v1/transactions").header("Authorization", bearer(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("amount", 10, "type", "EXPENSE", "categoryId", category.getId().toString(),
                "description", "Café", "transactionDate", LocalDate.now().toString(),
                "frequency", count > 1 ? "MONTHLY" : "ONCE", "repeatCount", count))));
    }

    private ResultActions createGoal(Session s) throws Exception {
        return mockMvc.perform(post("/api/v1/goals").header("Authorization", bearer(s))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("name", "Viagem", "targetAmount", 5000, "deadline", LocalDate.now().plusYears(1).toString()))));
    }

    private ResultActions webhook(String type, String id) throws Exception {
        return mockMvc.perform(post("/api/webhooks/mercadopago").param("data.id", id).param("type", type)
            .header("x-signature", "ts=1,v1=abc").header("x-request-id", "req")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("type", type, "data", Map.of("id", id)))));
    }
}
