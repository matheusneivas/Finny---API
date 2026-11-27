package com.finny.api.security;

import com.finny.api.application.service.EmailVerificationPolicy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Bloqueia as rotas de dados (403, code EMAIL_NOT_VERIFIED) até a pessoa confirmar o e-mail.
 * Ficam livres: autenticação (inclui verificar/reenviar), perfil, status do onboarding, foto e push.
 */
@Component
public class EmailVerificationFilter extends OncePerRequestFilter {

    public static final String CODE = "EMAIL_NOT_VERIFIED";

    private final EmailVerificationPolicy policy;
    private final ErrorResponseWriter errorResponseWriter;

    public EmailVerificationFilter(EmailVerificationPolicy policy, ErrorResponseWriter errorResponseWriter) {
        this.policy = policy;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!policy.enabled() || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/")
            || uri.startsWith("/api/v1/auth/")
            || uri.equals("/api/v1/users/profile")
            || uri.equals("/api/v1/users/me/avatar")
            || uri.equals("/api/v1/onboarding/status")
            || uri.startsWith("/api/v1/notifications/token");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails user) {
            UUID userId;
            try {
                userId = UUID.fromString(user.getUsername());
            } catch (IllegalArgumentException e) {
                chain.doFilter(request, response);
                return;
            }
            if (!policy.isVerified(userId)) {
                errorResponseWriter.write(response, HttpStatus.FORBIDDEN,
                    "Confirme seu e-mail com o código que enviamos para continuar.", CODE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
