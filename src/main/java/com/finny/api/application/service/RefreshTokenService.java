package com.finny.api.application.service;

import com.finny.api.application.exception.UnauthorizedException;
import com.finny.api.domain.entity.RefreshToken;
import com.finny.api.domain.repository.RefreshTokenRepository;
import com.finny.api.security.JwtTokenProvider;
import com.finny.api.security.JwtTokenProvider.IssuedToken;
import com.finny.api.security.JwtTokenProvider.ParsedToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Rotação de refresh tokens: cada refresh token vale uma única vez.
 * Reuso de um token já trocado revoga a sessão inteira (família).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;

    public record Rotation(UUID userId, IssuedToken refreshToken) {}

    /** Novo login: cria uma família nova. */
    @Transactional
    public IssuedToken issueForNewSession(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    /** Troca um refresh token válido por um novo (o antigo deixa de valer). */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        ParsedToken parsed = tokenProvider.parseRefreshToken(rawToken)
            .orElseThrow(() -> new UnauthorizedException("Refresh token inválido ou expirado"));

        RefreshToken stored = refreshTokenRepository.findById(parsed.jti())
            .filter(t -> t.getUserId().equals(parsed.userId()))
            .orElseThrow(() -> new UnauthorizedException("Refresh token inválido ou expirado"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token inválido ou expirado");
        }
        if (stored.isRevoked() || refreshTokenRepository.revokeIfActive(stored.getId(), Instant.now()) == 0) {
            // Token já usado/revogado apareceu de novo: possível vazamento. Derruba a sessão inteira.
            int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId(), Instant.now());
            log.warn("Reuso de refresh token detectado | user: {} | tokens revogados: {}", stored.getUserId(), revoked);
            throw new UnauthorizedException("Sessão expirada. Faça login novamente.");
        }
        return new Rotation(stored.getUserId(), issue(stored.getUserId(), stored.getFamilyId()));
    }

    /** Logout: revoga a sessão do refresh token informado (silencioso se já inválido). */
    @Transactional
    public void revokeSession(String rawToken) {
        tokenProvider.parseRefreshToken(rawToken)
            .flatMap(parsed -> refreshTokenRepository.findById(parsed.jti())
                .filter(t -> t.getUserId().equals(parsed.userId())))
            .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    /** Limpeza diária de tokens vencidos há mais de 7 dias. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Refresh tokens vencidos removidos: {}", removed);
        }
    }

    private IssuedToken issue(UUID userId, UUID familyId) {
        IssuedToken issued = tokenProvider.generateRefreshToken(userId);
        refreshTokenRepository.save(new RefreshToken(
            issued.jti(), userId, familyId, issued.expiresAt(), Instant.now(), null));
        return issued;
    }
}
