package com.finny.api.integration;

import com.finny.api.domain.entity.User;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.mail.MailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "app.auth.require-email-verification=true")
class EmailVerificationIntegrationTest extends IntegrationTestBase {

    @MockitoBean
    private MailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationSendsCodeAndBlocksDataUntilVerified() throws Exception {
        Session session = register();
        String code = lastCodeSentTo(session.email());

        // Rotas de dados bloqueadas, com código estável para o app
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearer(session)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        // Perfil e foto continuam livres
        mockMvc.perform(get("/api/v1/users/me/avatar").header("Authorization", bearer(session)))
            .andExpect(status().isNotFound());

        // Código errado conta tentativa
        verify(session, wrong(code)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("4 tentativas")));

        verify(session, code).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", bearer(session)))
            .andExpect(status().isOk());

        // Código já usado não serve de novo (e verificar de novo é inofensivo)
        verify(session, code).andExpect(status().isOk());
    }

    @Test
    void fiveWrongAttemptsBurnTheCode() throws Exception {
        Session session = register();
        String code = lastCodeSentTo(session.email());
        for (int i = 0; i < 5; i++) {
            verify(session, wrong(code)).andExpect(status().isBadRequest());
        }
        verify(session, code).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Peça um novo")));
    }

    @Test
    void resendHasCooldownWithRetryAfter() throws Exception {
        Session session = register();
        mockMvc.perform(post("/api/v1/auth/email/resend").header("Authorization", bearer(session)))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void codesAreNotStoredInPlainText() throws Exception {
        Session session = register();
        String code = lastCodeSentTo(session.email());
        List<String> hashes = jdbc("select code_hash from email_codes");
        assertThat(hashes).isNotEmpty().allSatisfy(h -> assertThat(h).hasSize(64).doesNotContain(code));
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        Session session = register();
        verify(session, lastCodeSentTo(session.email())).andExpect(status().isOk());

        // E-mail inexistente responde igual
        forgot("ninguem-" + System.nanoTime() + "@example.com").andExpect(status().isNoContent());
        forgot(session.email().toUpperCase()).andExpect(status().isNoContent());
        forgot(session.email()).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));

        String code = lastCodeSentTo(session.email());

        // Senha fraca: mesma regra do cadastro
        reset(session.email(), code, "fraca").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.newPassword").exists());
        reset(session.email(), wrong(code), "NovaSenha1").andExpect(status().isBadRequest());
        reset("ninguem@example.com", code, "NovaSenha1").andExpect(status().isBadRequest());

        reset(session.email(), code, "NovaSenha1").andExpect(status().isNoContent());

        // Sessões antigas encerradas; senha antiga não entra; nova entra
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", session.refreshToken()))))
            .andExpect(status().isUnauthorized());
        login(session.email(), "Senha123").andExpect(status().isUnauthorized());
        login(session.email(), "NovaSenha1").andExpect(status().isOk());

        // Código de recuperação não serve duas vezes
        reset(session.email(), code, "OutraSenha2").andExpect(status().isBadRequest());
    }

    @Test
    void unverifiedUserGetsNewCodeOnLoginAndExistingVerifiedUsersAreFree() throws Exception {
        Session session = register();
        User user = userRepository.findById(session.userId()).orElseThrow();
        assertThat(user.getEmailVerified()).isFalse();

        // Login com código ainda válido não manda outro
        clearInvocations(mailSender);
        login(session.email(), "Senha123").andExpect(status().isOk())
            .andExpect(jsonPath("$.user.emailVerified").value(false));
        verifyNoInteractions(mailSender);

        // Conta já verificada (ex.: antiga) entra direto
        user.setEmailVerified(true);
        userRepository.save(user);
        MvcResult login = login(session.email(), "Senha123").andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    // ── Auxiliares ─────────────────────────────────────────────────────────

    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailSender, atLeastOnce()).send(eq(email), subject.capture(), anyString(), anyString());
        String s = subject.getValue();
        assertThat(s).matches("\\d{6} é seu código do Fini");
        return s.substring(0, 6);
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private org.springframework.test.web.servlet.ResultActions verify(Session s, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email/verify").header("Authorization", bearer(s))
            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", code))));
    }

    private org.springframework.test.web.servlet.ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot")
            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email))));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String email, String code, String pwd) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", email, "code", code, "newPassword", pwd))));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String pwd) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("email", email, "password", pwd))));
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private List<String> jdbc(String sql) {
        return jdbcTemplate.queryForList(sql, String.class);
    }
}
