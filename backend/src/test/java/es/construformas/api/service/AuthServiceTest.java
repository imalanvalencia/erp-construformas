package es.construformas.api.service;

import es.construformas.api.dto.AuthResponse;
import es.construformas.api.dto.LoginRequest;
import es.construformas.api.model.RefreshToken;
import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.repository.RefreshTokenRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @InjectMocks private AuthService authService;

    private User buildUser(String email, String password, Set<Role> roles) {
        return User.builder()
                .id(1L).email(email).password(password)
                .roles(roles).name("Test").active(true).build();
    }

    @Test
    @DisplayName("Login with email and correct credentials should return token")
    void shouldLoginSuccessfullyWithEmail() {
        Role adminRole = Role.builder().id(1L).name("ADMIN").build();
        User user = buildUser("test@test.com", "hashed", Set.of(adminRole));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("test@test.com", List.of("ADMIN"))).thenReturn("token123");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("password");
        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("token123");
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getRoles()).containsExactly("ADMIN");
        assertThat(response.getName()).isEqualTo("Test");
        assertThat(response.getId()).isEqualTo(1L);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Refresh with valid token should rotate tokens")
    void shouldRefreshTokens() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = buildUser("op@test.com", "hashed", Set.of(opRole));
        RefreshToken oldToken = RefreshToken.builder()
                .id("old-id").user(user).token("old-refresh")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(refreshTokenRepository.findByToken("old-refresh"))
                .thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(jwtUtil.generateToken("op@test.com", List.of("OPERATOR")))
                .thenReturn("new-access");

        AuthResponse response = authService.refreshToken("old-refresh");

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isNotEqualTo("old-refresh");
        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(response.isMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("Refresh with revoked token should throw")
    void shouldRejectRevokedToken() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = buildUser("op@test.com", "hashed", Set.of(opRole));
        RefreshToken revokedToken = RefreshToken.builder()
                .id("rev-id").user(user).token("revoked")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(true).build();

        when(refreshTokenRepository.findByToken("revoked"))
                .thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refreshToken("revoked"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token has been revoked");
    }

    @Test
    @DisplayName("Refresh with expired token should throw")
    void shouldRejectExpiredToken() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = buildUser("op@test.com", "hashed", Set.of(opRole));
        RefreshToken expiredToken = RefreshToken.builder()
                .id("exp-id").user(user).token("expired")
                .expiry(LocalDateTime.now().minusDays(1)).revoked(false).build();

        when(refreshTokenRepository.findByToken("expired"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refreshToken("expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token has expired");
    }

    @Test
    @DisplayName("Logout should revoke refresh token")
    void shouldRevokeTokenOnLogout() {
        RefreshToken token = RefreshToken.builder()
                .id("tok-id").token("my-token").revoked(false)
                .expiry(LocalDateTime.now().plusDays(7)).build();

        when(refreshTokenRepository.findByToken("my-token"))
                .thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        authService.logout("my-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("Login with nie should return token")
    void shouldLoginSuccessfullyWithNie() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = User.builder()
                .id(2L).email("op@test.com").password("hashed").nie("12345678A")
                .roles(Set.of(opRole)).name("Op").active(true).build();

        when(userRepository.findByNie("12345678A")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("op@test.com", List.of("OPERATOR"))).thenReturn("tokenNie");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setNie("12345678A");
        request.setPassword("pass");
        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("tokenNie");
        assertThat(response.getRoles()).containsExactly("OPERATOR");
    }

    @Test
    @DisplayName("Login with wrong password should throw")
    void shouldRejectInvalidPassword() {
        Role adminRole = Role.builder().id(1L).name("ADMIN").build();
        User user = buildUser("test@test.com", "hashed", Set.of(adminRole));

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("wrong");
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    @DisplayName("Login with no email and no nie should throw")
    void shouldRejectMissingCredentials() {
        LoginRequest request = new LoginRequest();
        request.setPassword("pass");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email or NIE is required");
    }

    @Test
    @DisplayName("S9: login with an inactive user should be rejected with 403")
    void shouldRejectInactiveUser() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = User.builder().id(3L).email("off@test.com").password("hashed")
                .roles(Set.of(opRole)).name("Off").active(false).build();

        when(userRepository.findByEmail("off@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setEmail("off@test.com");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("User is inactive");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("S10: login should surface mustChangePassword")
    void loginShouldSurfaceMustChangePassword() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = User.builder().id(4L).email("temp@test.com").password("hashed")
                .roles(Set.of(opRole)).name("Temp").active(true).mustChangePassword(true).build();

        when(userRepository.findByEmail("temp@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("temp-pass", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("temp@test.com", List.of("OPERATOR"))).thenReturn("temp-access");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setEmail("temp@test.com");
        request.setPassword("temp-pass");

        AuthResponse response = authService.login(request);

        assertThat(response.isMustChangePassword()).isTrue();
    }
}
