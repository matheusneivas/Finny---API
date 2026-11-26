package com.finny.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Emite e valida JWTs.
 * <ul>
 *   <li>Access token: curto (padrão 30 min), claim {@code typ=access}. Único aceito nas rotas da API.</li>
 *   <li>Refresh token: longo (padrão 30 dias), claim {@code typ=refresh} e {@code jti} registrado no
 *       banco (rotação e revogação em {@link com.finny.api.application.service.RefreshTokenService}).</li>
 * </ul>
 */
@Component
@Slf4j
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    private static final String ISSUER = "finny-api";
    private static final int MIN_SECRET_BYTES = 32;

    /** Valores que já estiveram no repositório: nunca podem ser usados. */
    private static final Set<String> KNOWN_INSECURE_SECRETS = Set.of(
        "finny-jwt-secret-key-change-in-production-min-256-bits-long",
        "changeme", "secret");

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration:1800000}")
    private long accessExpirationMs;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpirationMs;

    private SecretKey signingKey;

    public record ParsedToken(UUID userId, String type, String jti, Instant expiresAt) {}

    public record IssuedToken(String token, String jti, Instant expiresAt) {}

    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "JWT_SECRET ausente ou curto demais (mínimo " + MIN_SECRET_BYTES + " bytes). "
                    + "Gere um com: openssl rand -base64 48");
        }
        if (KNOWN_INSECURE_SECRETS.contains(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET usa um valor de exemplo público. Gere um novo segredo.");
        }
        signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken generateAccessToken(UUID userId) {
        return build(userId, TYPE_ACCESS, accessExpirationMs);
    }

    public IssuedToken generateRefreshToken(UUID userId) {
        return build(userId, TYPE_REFRESH, refreshExpirationMs);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    /** Valida assinatura, emissor e validade. Vazio se o token for inválido. */
    public Optional<ParsedToken> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String type = claims.get("typ", String.class);
            if (type == null || claims.getSubject() == null) {
                return Optional.empty();
            }
            return Optional.of(new ParsedToken(
                UUID.fromString(claims.getSubject()),
                type,
                claims.getId(),
                claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rejeitado: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Somente access tokens válidos (refresh token não serve para acessar a API). */
    public Optional<ParsedToken> parseAccessToken(String token) {
        return parse(token).filter(t -> TYPE_ACCESS.equals(t.type()));
    }

    public Optional<ParsedToken> parseRefreshToken(String token) {
        return parse(token).filter(t -> TYPE_REFRESH.equals(t.type()) && t.jti() != null);
    }

    private IssuedToken build(UUID userId, String type, long ttlMs) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(ttlMs);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
            .issuer(ISSUER)
            .subject(userId.toString())
            .id(jti)
            .claim("typ", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
        return new IssuedToken(token, jti, expiresAt);
    }
}
