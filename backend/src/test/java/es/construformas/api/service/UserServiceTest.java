package es.construformas.api.service;

import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.dto.ChangePasswordRequest;
import es.construformas.api.dto.SessionDTO;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
import es.construformas.api.dto.UserUpdateRequest;
import es.construformas.api.exception.UserNotFoundException;
import es.construformas.api.model.Project;
import es.construformas.api.model.RefreshToken;
import es.construformas.api.repository.ProjectRepository;
import es.construformas.api.repository.RefreshTokenRepository;
import es.construformas.api.repository.RoleRepository;
import es.construformas.api.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @InjectMocks private UserService userService;

    /** This is a pure Mockito unit test, so the security context is set by hand. */
    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create user should save and return")
    void shouldCreateUser() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        User user = User.builder().name("Test").email("test@test.com").password("raw")
                .roles(Set.of(opRole)).build();
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("raw")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = userService.create(user);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("Create user without roles should default to OPERATOR")
    void shouldDefaultToOperatorRole() {
        Role opRole = Role.builder().id(2L).name("OPERATOR").build();
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(roleRepository.findByName("OPERATOR")).thenReturn(Optional.of(opRole));
        when(passwordEncoder.encode("raw")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        User user = User.builder().name("Test").email("test@test.com").password("raw").build();
        User result = userService.create(user);

        assertThat(result.getRoles()).containsExactly(opRole);
    }

    @Test
    @DisplayName("Create user with existing email should throw")
    void shouldNotCreateDuplicateEmail() {
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        User user = User.builder().name("Dup").email("dup@test.com").role(UserRole.OPERATOR).build();
        assertThatThrownBy(() -> userService.create(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");
    }

    @Test
    @DisplayName("Create user with existing NIE should throw")
    void shouldNotCreateDuplicateNie() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(userRepository.existsByNie("12345678A")).thenReturn(true);

        User user = User.builder().name("Dup").email("test@test.com")
                .nie("12345678A").role(UserRole.OPERATOR).build();

        assertThatThrownBy(() -> userService.create(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("NIE already exists");
    }

    @Test
    @DisplayName("Find by ID should return user when exists")
    void shouldFindById() {
        User user = User.builder().id(1L).name("Test").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test");
    }

    @Test
    @DisplayName("S3: Find by ID should throw UserNotFoundException when not found")
    void shouldThrowWhenNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Find all should delegate to repository")
    void shouldFindAll() {
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().id(1L).name("A").build(),
                User.builder().id(2L).name("B").build()
        ));

        var result = userService.findAll();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("Find by role should delegate to repository")
    void shouldFindByRole() {
        when(userRepository.findByRole(UserRole.ADMIN)).thenReturn(List.of(
                User.builder().id(1L).role(UserRole.ADMIN).build()
        ));

        var result = userService.findByRole(UserRole.ADMIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("Update should merge non-null fields and save")
    void shouldUpdateUser() {
        User existing = User.builder().id(1L).name("Old").phone("111").nie("A").active(true).build();
        UserUpdateRequest updated = UserUpdateRequest.builder()
                .name("New").phone("222").nie("B").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.update(1L, updated);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPhone()).isEqualTo("222");
        assertThat(result.getNie()).isEqualTo("B");
    }

    @Test
    @DisplayName("S5: Update should apply email and roles but never availability")
    void shouldApplyEmailAndRolesButIgnoreAvailabilityOnUpdate() {
        Role admin = Role.builder().id(1L).name("ADMIN").build();
        Role operator = Role.builder().id(2L).name("OPERATOR").build();
        User existing = User.builder().id(1L).name("Old").email("old@test.com")
                .password("encoded").roles(Set.of(operator))
                .availability(UserAvailability.AVAILABLE).active(true).build();
        UserUpdateRequest updated = UserUpdateRequest.builder()
                .name("New").email("new@test.com")
                .roles(Set.of(admin, operator))
                .availability(UserAvailability.ON_SITE)
                .active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.update(1L, updated);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getRoles()).containsExactlyInAnyOrder(admin, operator);
        assertThat(result.getAvailability()).isEqualTo(UserAvailability.AVAILABLE);
        assertThat(result.getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("S2: Update to an email owned by another user should be rejected")
    void shouldRejectDuplicateEmailOnUpdate() {
        User existing = User.builder().id(1L).name("Old").email("old@test.com").active(true).build();
        UserUpdateRequest updated = UserUpdateRequest.builder().email("taken@test.com").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L, updated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("S3: findByIdForCaller should return any user for an ADMIN")
    void findByIdForCallerShouldAllowAdmin() {
        authenticateAs("admin@test.com");
        User adminCaller = User.builder().id(9L).email("admin@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();
        User target = User.builder().id(1L).name("Target").build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminCaller));
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));

        assertThat(userService.findByIdForCaller(1L)).isSameAs(target);
    }

    @Test
    @DisplayName("S3: findByIdForCaller should reject an OPERATOR reading another user")
    void findByIdForCallerShouldRejectOperatorReadingOtherUser() {
        authenticateAs("op@test.com");
        User operatorCaller = User.builder().id(9L).email("op@test.com")
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();

        when(userRepository.findByEmail("op@test.com")).thenReturn(Optional.of(operatorCaller));

        assertThatThrownBy(() -> userService.findByIdForCaller(1L))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("S3: findByIdForCaller should let an OPERATOR read its own record")
    void findByIdForCallerShouldAllowOperatorOwnRecord() {
        authenticateAs("op@test.com");
        User operatorCaller = User.builder().id(9L).email("op@test.com")
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();

        when(userRepository.findByEmail("op@test.com")).thenReturn(Optional.of(operatorCaller));
        when(userRepository.findById(9L)).thenReturn(Optional.of(operatorCaller));

        assertThat(userService.findByIdForCaller(9L)).isSameAs(operatorCaller);
    }

    @Test
    @DisplayName("Delete should delegate to repository")
    void shouldDeleteUser() {
        userService.delete(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    @DisplayName("S2: getProfile should resolve the authenticated caller")
    void getProfileShouldResolveCaller() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).name("Worker").email("op@test.com")
                .phone("555").nie("X1").availability(UserAvailability.ON_SITE).active(true)
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));

        UserProfileDTO profile = userService.getProfile();

        assertThat(profile.getId()).isEqualTo(7L);
        assertThat(profile.getName()).isEqualTo("Worker");
        assertThat(profile.getEmail()).isEqualTo("op@test.com");
        assertThat(profile.getPhone()).isEqualTo("555");
        assertThat(profile.getNie()).isEqualTo("X1");
        assertThat(profile.getAvailability()).isEqualTo(UserAvailability.ON_SITE);
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.getCurrentProjectId()).isNull();
    }

    @Test
    @DisplayName("S3: updateProfile should apply only phone and nie")
    void updateProfileShouldApplyOnlyContactFields() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").phone("111").nie("OLD")
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDTO profile = userService.updateProfile(
                new UserProfileUpdateRequest("222", "NEW"));

        assertThat(profile.getPhone()).isEqualTo("222");
        assertThat(profile.getNie()).isEqualTo("NEW");
        assertThat(caller.getRoles()).hasSize(1);
        assertThat(caller.isActive()).isTrue();
        assertThat(profile.getRoles()).hasSize(1);
    }

    @Test
    @DisplayName("S3: updateProfile should clear a contact field when sent empty")
    void updateProfileShouldClearOnBlank() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").phone("111").nie("OLD").build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDTO profile = userService.updateProfile(
                new UserProfileUpdateRequest("", ""));

        assertThat(profile.getPhone()).isNull();
        assertThat(profile.getNie()).isNull();
    }

    @Test
    @DisplayName("S3: updateProfile should keep a field it was not sent")
    void updateProfileShouldKeepUnsentField() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").phone("111").nie("OLD").build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDTO profile = userService.updateProfile(
                new UserProfileUpdateRequest(null, "NEW"));

        assertThat(profile.getPhone()).isEqualTo("111");
        assertThat(profile.getNie()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("S3: updateProfile should reject a phone owned by another user")
    void updateProfileShouldRejectDuplicatePhone() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").phone("111").build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.existsByPhone("222")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateProfile(
                new UserProfileUpdateRequest("222", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("S4: changeMyPassword should reject a wrong current password")
    void changeMyPasswordShouldRejectWrongCurrentPassword() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").password("hashed").build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com"))
                .thenReturn(Optional.of(caller));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeMyPassword(
                new ChangePasswordRequest("wrong", "new-pass")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current password is incorrect");

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("S4: changeMyPassword should encode, clear the flag and revoke sessions")
    void changeMyPasswordShouldEncodeAndRevokeSessions() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").password("hashed")
                .mustChangePassword(true).build();
        RefreshToken kept = RefreshToken.builder().id("a").user(caller).token("tok-a")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        RefreshToken other = RefreshToken.builder().id("b").user(caller).token("tok-b")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com"))
                .thenReturn(Optional.of(caller));
        when(passwordEncoder.matches("current", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(7L))
                .thenReturn(List.of(kept, other));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        userService.changeMyPassword(new ChangePasswordRequest("current", "new-pass"));

        assertThat(caller.getPassword()).isEqualTo("new-hash");
        assertThat(caller.isMustChangePassword()).isFalse();
        assertThat(kept.isRevoked()).isTrue();
        assertThat(other.isRevoked()).isTrue();
        verify(userRepository).save(caller);
        verify(refreshTokenRepository).save(kept);
        verify(refreshTokenRepository).save(other);
    }

    @Test
    @DisplayName("S8: listSessions should return only live tokens, without the token value")
    void listSessionsShouldReturnActiveTokensOnly() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").build();
        RefreshToken live = RefreshToken.builder().id("live").user(caller).token("secret-live")
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        RefreshToken stale = RefreshToken.builder().id("stale").user(caller).token("secret-stale")
                .expiry(LocalDateTime.now().minusDays(1)).revoked(false).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com"))
                .thenReturn(Optional.of(caller));
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(7L))
                .thenReturn(List.of(live, stale));

        List<SessionDTO> sessions = userService.listSessions();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getId()).isEqualTo("live");
        assertThat(sessions.get(0).getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
        assertThat(sessions.get(0).getExpiry()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("S8: closeOtherSessions should revoke every token but the current one")
    void closeOtherSessionsShouldKeepTheCurrentOne() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").build();
        RefreshToken current = RefreshToken.builder().id("keep").user(caller).token("keep-me")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        RefreshToken other = RefreshToken.builder().id("kill").user(caller).token("kill-me")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com"))
                .thenReturn(Optional.of(caller));
        when(refreshTokenRepository.findByUserId(7L)).thenReturn(List.of(current, other));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        userService.closeOtherSessions("keep-me");

        assertThat(current.isRevoked()).isFalse();
        assertThat(other.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(other);
        verify(refreshTokenRepository, never()).save(current);
    }

    @Test
    @DisplayName("S8: closeOtherSessions should reject a token that is not the caller's")
    void closeOtherSessionsShouldRejectForeignToken() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").build();
        RefreshToken current = RefreshToken.builder().id("keep").user(caller).token("keep-me")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();
        when(userRepository.findWithCurrentProjectByEmail("op@test.com"))
                .thenReturn(Optional.of(caller));
        when(refreshTokenRepository.findByUserId(7L)).thenReturn(List.of(current));

        assertThatThrownBy(() -> userService.closeOtherSessions("alien-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token not found");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ S9: active

    @Test
    @DisplayName("S9: update should keep the current active value when the field is omitted")
    void updateShouldKeepActiveWhenOmitted() {
        User existing = User.builder().id(1L).name("Old").active(true).build();
        UserUpdateRequest request = UserUpdateRequest.builder().name("New").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.update(1L, request);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("S9: update should route an explicit active change through the guards")
    void updateShouldApplyActiveWhenSent() {
        authenticateAs("admin@test.com");
        User caller = User.builder().id(9L).email("admin@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();
        User existing = User.builder().id(1L).name("Old").active(true)
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();
        UserUpdateRequest request = UserUpdateRequest.builder().active(false).build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(userService.update(1L, request).isActive()).isFalse();
    }

    @Test
    @DisplayName("S9: setActive should activate an inactive user without consulting the guards")
    void setActiveShouldActivate() {
        User target = User.builder().id(1L).active(false).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(userService.setActive(1L, true).isActive()).isTrue();

        verify(userRepository, never()).countActiveAdmins();
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("S9: setActive should refuse deactivating yourself")
    void setActiveShouldRefuseSelfDeactivation() {
        authenticateAs("admin@test.com");
        User caller = User.builder().id(9L).email("admin@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.findById(9L)).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> userService.setActive(9L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own account");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).countActiveAdmins();
    }

    @Test
    @DisplayName("S9: setActive should refuse leaving the system without an active ADMIN")
    void setActiveShouldRefuseLastActiveAdmin() {
        authenticateAs("root@test.com");
        User caller = User.builder().id(9L).email("root@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();
        User target = User.builder().id(2L).active(true)
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();

        when(userRepository.findByEmail("root@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> userService.setActive(2L, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("last active administrator");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("S9: setActive should deactivate an operator when another admin asks")
    void setActiveShouldDeactivateAnOperator() {
        authenticateAs("root@test.com");
        User caller = User.builder().id(9L).email("root@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build();
        User target = User.builder().id(3L).active(true)
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();

        when(userRepository.findByEmail("root@test.com")).thenReturn(Optional.of(caller));
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(userService.setActive(3L, false).isActive()).isFalse();

        verify(userRepository, never()).countActiveAdmins();
    }

    // -------------------------------------------------------------- S10: reset

    @Test
    @DisplayName("S10: resetPassword should hash the returned password, set the flag and revoke sessions")
    void resetPasswordShouldHashSetFlagAndRevokeSessions() {
        User target = User.builder().id(4L).email("op@test.com").password("old-hash")
                .mustChangePassword(false).build();
        RefreshToken live = RefreshToken.builder().id("t1").user(target).token("tok-1")
                .expiry(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(userRepository.findById(4L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(any(String.class))).thenReturn("new-hash");
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(4L)).thenReturn(List.of(live));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String temporaryPassword = userService.resetPassword(4L);

        assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(8);
        verify(passwordEncoder).encode(temporaryPassword);
        assertThat(target.getPassword()).isEqualTo("new-hash");
        assertThat(target.isMustChangePassword()).isTrue();
        assertThat(live.isRevoked()).isTrue();
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("S10: resetPassword should reject an unknown user")
    void resetPasswordShouldRejectUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetPassword(99L))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    // -------------------------------------------------------- S11 / S6: current project

    @Test
    @DisplayName("S11: setCurrentProject should persist the chosen project for any user")
    void setCurrentProjectShouldPersistTheProject() {
        User target = User.builder().id(1L).active(true).build();
        Project project = Project.builder().id(7L).name("Obra 7").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(projectRepository.findById(7L)).thenReturn(Optional.of(project));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.setCurrentProject(1L, 7L);

        assertThat(result.getCurrentProject()).isSameAs(project);
        verify(userRepository).save(target);
    }

    @Test
    @DisplayName("S11: setCurrentProject should reject an unknown project")
    void setCurrentProjectShouldRejectUnknownProject() {
        User target = User.builder().id(1L).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setCurrentProject(1L, 404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("S6: switchMyCurrentProject should accept a project the operator is assigned to")
    void switchMyCurrentProjectShouldAcceptAssignedProject() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com")
                .roles(Set.of(Role.builder().id(2L).name("OPERATOR").build())).build();
        Project project = Project.builder().id(5L).name("Obra 5").build();

        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByProjectIdAndOperatorId(5L, 7L)).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDTO profile = userService.switchMyCurrentProject(5L);

        assertThat(profile.getCurrentProjectId()).isEqualTo(5L);
        assertThat(profile.getCurrentProjectName()).isEqualTo("Obra 5");
        verify(userRepository).save(caller);
    }

    @Test
    @DisplayName("S6: switchMyCurrentProject should reject a project the operator is not assigned to")
    void switchMyCurrentProjectShouldRejectUnassignedProject() {
        authenticateAs("op@test.com");
        User caller = User.builder().id(7L).email("op@test.com").build();
        Project project = Project.builder().id(5L).name("Obra 5").build();

        when(userRepository.findWithCurrentProjectByEmail("op@test.com")).thenReturn(Optional.of(caller));
        when(projectRepository.findById(5L)).thenReturn(Optional.of(project));
        when(projectRepository.existsByProjectIdAndOperatorId(5L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> userService.switchMyCurrentProject(5L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not assigned");

        verify(userRepository, never()).save(any());
    }
}
