package com.finny.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limit em memória (janela fixa de 1 minuto; a foto de perfil usa janela de 1 hora).
 * <p>
 * Protege contra força bruta no login e contra abuso do chat (custo de IA).
 * Em mais de uma instância, troque por um armazenamento compartilhado (ex.: Redis/Bucket4j).
 * Atrás de proxy/load balancer, configure FORWARD_HEADERS_STRATEGY=framework para usar o IP real.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE_MS = Duration.ofMinutes(1).toMillis();
    private static final long HOUR_MS = Duration.ofHours(1).toMillis();
    private static final String AVATAR_PATH = "/api/v1/users/me/avatar";

    private final RateLimitProperties properties;
    private final ErrorResponseWriter errorResponseWriter;
    private final Clock clock;
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(2))
        .maximumSize(200_000)
        .build();
    private final Cache<String, AtomicInteger> hourlyCounters = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(2))
        .maximumSize(100_000)
        .build();

    public RateLimitFilter(RateLimitProperties properties, ErrorResponseWriter errorResponseWriter, Clock clock) {
        this.properties = properties;
        this.errorResponseWriter = errorResponseWriter;
        this.clock = clock;
    }

    private record Rule(String name, int limit, boolean perIp, long windowMs) {
        Rule(String name, int limit, boolean perIp) {
            this(name, limit, perIp, MINUTE_MS);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !request.getRequestURI().startsWith("/api/")
            || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = ruleFor(request);
        String subject = rule.perIp() ? "ip:" + request.getRemoteAddr() : subject(request);

        long now = clock.millis();
        long windowMs = rule.windowMs();
        long window = now / windowMs;
        String key = rule.name() + '|' + subject + '|' + window;
        Cache<String, AtomicInteger> store = windowMs == MINUTE_MS ? counters : hourlyCounters;
        int count = store.get(key, k -> new AtomicInteger()).incrementAndGet();

        if (count > rule.limit()) {
            long retryAfter = Math.max(1, ((window + 1) * windowMs - now + 999) / 1000);
            if (count == rule.limit() + 1) {
                log.warn("Rate limit atingido | regra: {} | {}", rule.name(), subject);
            }
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            errorResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS,
                "Muitas requisições. Tente novamente em " + retryAfter + " segundos.");
            return;
        }

        chain.doFilter(request, response);
    }

    private Rule ruleFor(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean post = "POST".equalsIgnoreCase(request.getMethod());
        if (post && (uri.equals("/api/v1/auth/login") || uri.equals("/api/v1/auth/register"))) {
            return new Rule("auth", properties.getAuthPerMinute(), true);
        }
        if (post && (uri.startsWith("/api/v1/auth/password/") || uri.startsWith("/api/v1/auth/email/"))) {
            return new Rule("auth-code", properties.getAuthPerMinute(), true);
        }
        if (post && uri.equals("/api/v1/auth/refresh")) {
            return new Rule("refresh", properties.getRefreshPerMinute(), true);
        }
        if (post && uri.equals("/api/v1/users/me/delete")) {
            // Pede a senha: limite baixo contra adivinhação com um token roubado
            return new Rule("account-delete", 5, false);
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && uri.equals("/api/v1/users/me/export")) {
            return new Rule("export", 5, false);
        }
        if (post && uri.equals("/api/v1/chat/messages")) {
            return new Rule("chat", properties.getChatPerMinute(), false);
        }
        if (post && uri.startsWith("/api/v1/ocr/")) {
            return new Rule("upload", properties.getUploadPerMinute(), false);
        }
        if ("PUT".equalsIgnoreCase(request.getMethod()) && uri.equals(AVATAR_PATH)) {
            return new Rule("avatar", properties.getAvatarPerHour(), false, HOUR_MS);
        }
        return new Rule("default", properties.getDefaultPerMinute(), false);
    }

    private static String subject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails user) {
            return "user:" + user.getUsername();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
