package com.finny.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static JwtTokenProvider provider(String secret) {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        ReflectionTestUtils.setField(provider, "accessExpirationMs", 60_000L);
        ReflectionTestUtils.setField(provider, "refreshExpirationMs", 120_000L);
        provider.init();
        return provider;
    }

    @Test
    void accessTokenIsAcceptedOnlyAsAccess() {
        JwtTokenProvider provider = provider("a-very-long-test-secret-0123456789-abcdef");
        UUID userId = UUID.randomUUID();
        String access = provider.generateAccessToken(userId).token();

        assertThat(provider.parseAccessToken(access)).get().extracting(JwtTokenProvider.ParsedToken::userId).isEqualTo(userId);
        assertThat(provider.parseRefreshToken(access)).isEmpty();
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        JwtTokenProvider provider = provider("a-very-long-test-secret-0123456789-abcdef");
        String refresh = provider.generateRefreshToken(UUID.randomUUID()).token();

        assertThat(provider.parseAccessToken(refresh)).isEmpty();
        assertThat(provider.parseRefreshToken(refresh)).isPresent();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String foreign = provider("another-very-long-secret-0123456789-xyz").generateAccessToken(UUID.randomUUID()).token();
        assertThat(provider("a-very-long-test-secret-0123456789-abcdef").parse(foreign)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtTokenProvider provider = provider("a-very-long-test-secret-0123456789-abcdef");
        String token = provider.generateAccessToken(UUID.randomUUID()).token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(provider.parse(tampered)).isEmpty();
        assertThat(provider.parse("not-a-jwt")).isEmpty();
        assertThat(provider.parse(null)).isEmpty();
    }

    @Test
    void shortOrPublicSecretsFailAtStartup() {
        assertThatThrownBy(() -> provider("short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> provider("finny-jwt-secret-key-change-in-production-min-256-bits-long"))
            .isInstanceOf(IllegalStateException.class);
    }
}
