package com.finny.api.application.service;

import com.finny.api.domain.repository.UserRepository;
import com.finny.api.infrastructure.mail.MailProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Decide se a verificação de e-mail está valendo e guarda (por 1 minuto) quem já verificou.
 * Sem provedor de e-mail configurado, a verificação fica desligada para ninguém ficar preso.
 */
@Component
public class EmailVerificationPolicy {

    private final boolean enabled;
    private final UserRepository userRepository;
    private final Cache<UUID, Boolean> verified = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(1))
        .maximumSize(50_000)
        .build();

    public EmailVerificationPolicy(@Value("${app.auth.require-email-verification:true}") boolean required,
                                   MailProperties mail, UserRepository userRepository) {
        this.enabled = required && mail.enabled();
        this.userRepository = userRepository;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean isVerified(UUID userId) {
        if (!enabled) return true;
        return verified.get(userId, id -> userRepository.findById(id).map(u -> Boolean.TRUE.equals(u.getEmailVerified())).orElse(true));
    }

    public void evict(UUID userId) {
        verified.invalidate(userId);
    }
}
