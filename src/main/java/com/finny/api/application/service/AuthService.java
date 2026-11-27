package com.finny.api.application.service;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.RegisterRequest;
import com.finny.api.application.dto.response.AuthResponse;
import com.finny.api.application.exception.BusinessException;
import com.finny.api.application.exception.ResourceConflictException;
import com.finny.api.application.exception.ServiceUnavailableException;
import com.finny.api.application.util.CpfUtils;
import com.finny.api.application.util.PhoneUtils;
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
    private final PlanService planService;

    @org.springframework.beans.factory.annotation.Value("${app.cpf-secret:}")
    private String cpfSecret;

    /** "Esqueci a senha": 1 pedido por minuto por e-mail, exista a conta ou não (não revela cadastro). */
    private final Cache<String, Long> forgotCooldown = Caffeine.newBuilder()
        .expireAfterWrite(EmailCodeService.COOLDOWN)
        .maximumSize(100_000)
        .build();

    /**
     * Trava por conta contra adivinhação de senha vinda de vários IPs (o rate limit é por IP):
     * depois de {@value #MAX_LOGIN_FAILURES} erros em 15 minutos, o login daquele e-mail espera.
     * Vale também para e-mail inexistente, para não revelar se a conta existe.
     */
    static final int MAX_LOGIN_FAILURES = 10;
    private static final java.time.Duration LOGIN_LOCK_WINDOW = java.time.Duration.ofMinutes(15);
    private final Cache<String, java.util.concurrent.atomic.AtomicInteger> loginFailures = Caffeine.newBuilder()
        .expireAfterWrite(LOGIN_LOCK_WINDOW)
        .maximumSize(200_000)
        .build();

    /** Versão dos Termos de Uso e da Política de Privacidade que o cadastro aceita. */
    public static final String TERMS_VERSION = "2026-11-27";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("E-mail já cadastrado");
        }
        if (!CpfUtils.isValid(request.getCpf())) {
            throw new BusinessException("CPF inválido");
        }
        String phone = PhoneUtils.normalize(request.getPhone());
        if (phone == null) {
            throw new BusinessException("Telefone inválido. Use o DDD, ex.: (11) 98765-4321");
        }
        if (cpfSecret == null || cpfSecret.length() < 16 || cpfSecret.startsWith("finny-cpf-secret-change")) {
            log.error("CPF_SECRET ausente ou inseguro: cadastro bloqueado");
            throw new ServiceUnavailableException("Cadastro indisponível no momento. Tente de novo mais tarde.");
        }
        String cpfHash = CpfUtils.hash(request.getCpf(), cpfSecret);
        if (userRepository.existsByCpfHash(cpfHash)) {
            throw new ResourceConflictException("CPF já possui uma conta cadastrada");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setCpfHash(cpfHash);
        user.setPhone(phone);
        user.setPlan(UserPlan.FREE);
        user.setEmailVerified(false);
        if (Boolean.TRUE.equals(request.getAcceptedTerms())) {
            // Registro de quando a pessoa aceitou os Termos e a Política de Privacidade (LGPD)
            user.setTermsAcceptedAt(java.time.LocalDateTime.now());
            user.setTermsVersion(TERMS_VERSION);
        }
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

        java.util.concurrent.atomic.AtomicInteger failures = loginFailures.getIfPresent(email);
        if (failures != null && failures.get() >= MAX_LOGIN_FAILURES) {
            throw new TooManyRequestsException(
                "Muitas tentativas de login nesta conta. Espere 15 minutos ou use \"Esqueci a senha\".",
                LOGIN_LOCK_WINDOW.toSeconds());
        }

        // Lança BadCredentialsException (401 genérico) para e-mail inexistente ou senha errada
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            int count = loginFailures.get(email, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            if (count == MAX_LOGIN_FAILURES) {
                log.warn("Login travado por 15 min após {} senhas erradas seguidas", MAX_LOGIN_FAILURES);
            }
            throw e;
        }
        loginFailures.invalidate(email);

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
        UserPlan plan = planService.effectivePlan(user);
        response.setPlan(plan);
        if (plan == UserPlan.FREE) response.setPlanExpiresAt(null);
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
