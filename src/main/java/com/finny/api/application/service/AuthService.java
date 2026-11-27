package com.finny.api.application.service;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.RegisterRequest;
import com.finny.api.application.dto.response.AuthResponse;
import com.finny.api.application.exception.ResourceConflictException;
import com.finny.api.application.exception.TooManyRequestsException;
import com.finny.api.application.exception.UnauthorizedException;
import com.finny.api.application.exception.ValidationException;
import com.finny.api.application.dto.response.UserResponse;
import com.finny.api.domain.enums.EmailCodePurpose;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.finny.api.application.mapper.UserMapper;
import com.finny.api.domain.entity.User;
import com.finny.api.domain.enums.UserPlan;
import com.finny.api.domain.repository.UserRepository;
import com.finny.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final EmailVerificationPolicy verificationPolicy;

    /** "Esqueci a senha": 1 pedido por minuto por e-mail, exista a conta ou não (não revela cadastro). */
    private final Cache<String, Long> forgotCooldown = Caffeine.newBuilder()
        .expireAfterWrite(EmailCodeService.COOLDOWN)
        .maximumSize(100_000)
        .build();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("E-mail já cadastrado");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPlan(UserPlan.FREE);
        user.setEmailVerified(false);
        user = userRepository.save(user);

        log.info("Usuário registrado | id: {}", user.getId());
        if (verificationPolicy.enabled()) {
            emailCodeService.issue(user, EmailCodePurpose.VERIFY_EMAIL);
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Lança BadCredentialsException (401 genérico) para e-mail inexistente ou senha errada
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));

        User user = userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException("E-mail ou senha incorretos"));

        log.info("Login realizado | id: {}", user.getId());
        sendVerificationIfNeeded(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(request.getRefreshToken());

        User user = userRepository.findById(rotation.userId())
            .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));

        return AuthResponse.builder()
            .accessToken(tokenProvider.generateAccessToken(user.getId()).token())
            .refreshToken(rotation.refreshToken().token())
            .expiresIn(tokenProvider.getAccessExpirationSeconds())
            .user(toUserResponse(user))
            .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeSession(refreshToken);
        }
    }

    // ── Verificação de e-mail ───────────────────────────────────────────────

    @Transactional(noRollbackFor = ValidationException.class)
    public UserResponse verifyEmail(UUID userId, String code) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            emailCodeService.consume(userId, EmailCodePurpose.VERIFY_EMAIL, code);
            user.setEmailVerified(true);
            user = userRepository.save(user);
            verificationPolicy.evict(userId);
            log.info("E-mail verificado | id: {}", userId);
        }
        return toUserResponse(user);
    }

    @Transactional
    public void resendVerification(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("Sessão inválida"));
        if (Boolean.TRUE.equals(user.getEmailVerified()) || !verificationPolicy.enabled()) {
            return;
        }
        emailCodeService.issue(user, EmailCodePurpose.VERIFY_EMAIL);
    }

    // ── Esqueci minha senha ─────────────────────────────────────────────────

    /** Sempre termina igual (204), exista ou não a conta. */
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        long now = System.currentTimeMillis();
        Long last = forgotCooldown.asMap().putIfAbsent(email, now);
        if (last != null) {
            long wait = Math.max(1, EmailCodeService.COOLDOWN.toSeconds() - (now - last) / 1000);
            throw new TooManyRequestsException("Aguarde " + wait + " segundos para pedir outro código.", wait);
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            try {
                emailCodeService.issue(user, EmailCodePurpose.RESET_PASSWORD);
            } catch (TooManyRequestsException e) {
                // Limite diário: não respondemos diferente, para não revelar que a conta existe.
                log.warn("Recuperação de senha acima do limite | id: {}", user.getId());
            }
        });
    }

    @Transactional(noRollbackFor = ValidationException.class)
    public void resetPassword(String rawEmail, String code, String newPassword) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(rawEmail))
            .orElseThrow(() -> new ValidationException(EmailCodeService.INVALID));
        emailCodeService.consume(user.getId(), EmailCodePurpose.RESET_PASSWORD, code);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true); // quem recebeu o código controla a caixa de entrada
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());
        verificationPolicy.evict(user.getId());
        log.info("Senha redefinida por código | id: {} (sessões encerradas)", user.getId());
    }

    private void sendVerificationIfNeeded(User user) {
        if (!verificationPolicy.enabled() || Boolean.TRUE.equals(user.getEmailVerified())) return;
        if (emailCodeService.hasActiveCode(user.getId(), EmailCodePurpose.VERIFY_EMAIL)) return;
        try {
            emailCodeService.issue(user, EmailCodePurpose.VERIFY_EMAIL);
        } catch (TooManyRequestsException ignored) {
            // Já enviamos há pouco; o app tem o botão de reenviar.
        }
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = userMapper.toResponse(user);
        // Com a verificação desligada (sem provedor de e-mail), ninguém fica preso na tela de código.
        if (!verificationPolicy.enabled()) response.setEmailVerified(true);
        return response;
    }

    private AuthResponse issueTokens(User user) {
        return AuthResponse.builder()
            .accessToken(tokenProvider.generateAccessToken(user.getId()).token())
            .refreshToken(refreshTokenService.issueForNewSession(user.getId()).token())
            .expiresIn(tokenProvider.getAccessExpirationSeconds())
            .user(toUserResponse(user))
            .build();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
