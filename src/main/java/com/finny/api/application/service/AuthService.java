package com.finny.api.application.service;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.RegisterRequest;
import com.finny.api.application.dto.response.AuthResponse;
import com.finny.api.application.exception.ResourceConflictException;
import com.finny.api.application.exception.UnauthorizedException;
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
        user.setEmailVerified(false); // TODO: verificação de e-mail
        user = userRepository.save(user);

        log.info("Usuário registrado | id: {}", user.getId());
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
            .user(userMapper.toResponse(user))
            .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeSession(refreshToken);
        }
    }

    private AuthResponse issueTokens(User user) {
        return AuthResponse.builder()
            .accessToken(tokenProvider.generateAccessToken(user.getId()).token())
            .refreshToken(refreshTokenService.issueForNewSession(user.getId()).token())
            .expiresIn(tokenProvider.getAccessExpirationSeconds())
            .user(userMapper.toResponse(user))
            .build();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
