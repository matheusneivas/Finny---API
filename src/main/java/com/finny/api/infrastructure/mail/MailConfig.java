package com.finny.api.infrastructure.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
@Slf4j
public class MailConfig {

    @Bean
    MailSender mailSender(MailProperties properties, ObjectMapper objectMapper, Environment env) {
        String provider = properties.getProvider() == null ? "none" : properties.getProvider().trim().toLowerCase();
        switch (provider) {
            case "resend", "brevo" -> {
                if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                    throw new IllegalStateException("app.mail.provider=" + provider + " exige a chave (RESEND_API_KEY ou BREVO_API_KEY)");
                }
                log.info("E-mails via {} | remetente: {}", provider, properties.getFrom());
                return new HttpMailSender(properties, objectMapper);
            }
            case "log" -> {
                if (!env.acceptsProfiles(Profiles.of("dev", "test"))) {
                    throw new IllegalStateException("app.mail.provider=log só é permitido nos perfis dev/test (expõe códigos no log)");
                }
                return (to, subject, text, html) -> log.info("[DEV] E-mail para {} | {}", to, subject);
            }
            case "none" -> {
                log.warn("Envio de e-mail desligado (MAIL_PROVIDER=none): a verificação de e-mail fica desativada");
                return (to, subject, text, html) -> { };
            }
            default -> throw new IllegalStateException("app.mail.provider desconhecido: " + provider);
        }
    }
}
