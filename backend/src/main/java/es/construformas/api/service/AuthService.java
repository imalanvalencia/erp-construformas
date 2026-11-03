package es.construformas.api.service;

import es.construformas.api.dto.AuthResponse;
import es.construformas.api.dto.LoginRequest;
import es.construformas.api.model.RefreshToken;
import es.construformas.api.model.User;
import es.construformas.api.model.UserRole;
import es.construformas.api.repository.RefreshTokenRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final long REFRESH_TOKEN_DAYS = 14;

    public AuthResponse login(LoginRequest request) {
        User user = findUser(request);
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!user.isActive()) {
            // S9: credentials are checked first, so the 403 never leaks account status
            // to someone who does not already know the password.
            throw new AccessDeniedException("User is inactive");
        }
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName()).toList();
        if (roles.isEmpty()) {
            roles = List.of(UserRole.OPERATOR.name());
        }
        String accessToken = jwtUtil.generateToken(user.getEmail(), roles);
        String refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .id(user.getId())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .roles(roles)
                .name(user.getName())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (refreshToken.isRevoked()) {
            throw new IllegalArgumentException("Refresh token has been revoked");
        }
        if (refreshToken.getExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token has expired");
        }
        User user = refreshToken.getUser();
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName()).toList();
        String newAccessToken = jwtUtil.generateToken(user.getEmail(), roles);
        String newRefreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .id(user.getId())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .email(user.getEmail())
                .roles(roles)
                .name(user.getName())
                .mustChangePassword(user.isMustChangePassword())
                .build();
    }

    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String createRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .token(tokenValue)
                .expiry(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private User findUser(LoginRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            return userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        }
        if (request.getNie() != null && !request.getNie().isBlank()) {
            return userRepository.findByNie(request.getNie())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        }
        throw new IllegalArgumentException("Email or NIE is required");
    }
}
