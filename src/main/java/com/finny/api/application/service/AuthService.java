package com.finny.api.application.service;

import com.finny.api.application.dto.request.LoginRequest;
import com.finny.api.application.dto.request.RefreshTokenRequest;
import com.finny.api.application.dto.request.RegisterRequest;
import com.finny.api.application.dto.response.AuthResponse;
import com.finny.api.application.dto.response.UserResponse;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.debug("Registering new user with email: {}", request.getEmail());

        // Verificar se e-mail já existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException("E-mail já cadastrado");
        }

        // Criar novo usuário
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPlan(UserPlan.FREE);
        user.setEmailVerified(false); // TODO: Implementar verificação de e-mail

        user = userRepository.save(user);

        // Gerar tokens
        String accessToken = tokenProvider.generateToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        log.info("User registered successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userResponse)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.debug("User login attempt: {}", request.getEmail());

        // Autenticar usuário
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Buscar usuário
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("E-mail ou senha incorretos"));

        // Gerar tokens
        String accessToken = tokenProvider.generateToken(user.getId());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        UserResponse userResponse = userMapper.toResponse(user);

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .user(userResponse)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Token refresh attempt");

        String refreshToken = request.getRefreshToken();

        // Validar refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Refresh token inválido ou expirado");
        }

        // Verificar se é refresh token
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Token fornecido não é um refresh token");
        }

        // Extrair userId e gerar novos tokens
        var userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));

        String newAccessToken = tokenProvider.generateToken(userId);
        String newRefreshToken = tokenProvider.generateRefreshToken(userId);

        UserResponse userResponse = userMapper.toResponse(user);

        log.info("Token refreshed successfully for user: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .user(userResponse)
                .build();
    }

    public void logout() {
        // Stateless JWT - apenas limpar contexto
        SecurityContextHolder.clearContext();
        log.debug("User logged out");
    }
}
