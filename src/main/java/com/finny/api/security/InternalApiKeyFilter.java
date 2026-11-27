package com.finny.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Protege /api/internal/** (callbacks do n8n) com o header X-Internal-API-Key.
 * Sem chave configurada, as rotas internas ficam fechadas.
 */
@Component
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-API-Key";
    public static final String ROLE = "ROLE_INTERNAL";
    private static final int MIN_KEY_LENGTH = 32;

    private final byte[] expectedKey;
    private final ErrorResponseWriter errorResponseWriter;

    public InternalApiKeyFilter(@Value("${internal.api-key:}") String apiKey, ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
        if (apiKey == null || apiKey.length() < MIN_KEY_LENGTH) {
            log.warn("INTERNAL_API_KEY ausente ou curta: rotas /api/internal ficam bloqueadas");
            this.expectedKey = null;
        } else {
            this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        boolean valid = expectedKey != null && provided != null
            && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8));

        if (!valid) {
            log.warn("Chamada interna recusada | ip: {}", request.getRemoteAddr());
            errorResponseWriter.write(response, HttpStatus.UNAUTHORIZED, "Credencial interna inválida");
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "internal", null, List.of(new SimpleGrantedAuthority(ROLE))));
        chain.doFilter(request, response);
    }
}
