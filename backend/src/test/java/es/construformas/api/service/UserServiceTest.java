package es.construformas.api.service;

import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
import es.construformas.api.exception.UserNotFoundException;
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
        User updated = User.builder().name("New").phone("222").nie("B").active(false).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.update(1L, updated);

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPhone()).isEqualTo("222");
        assertThat(result.getNie()).isEqualTo("B");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("S2: Update should apply email, roles and availability")
    void shouldApplyEmailRolesAndAvailabilityOnUpdate() {
        Role admin = Role.builder().id(1L).name("ADMIN").build();
        Role operator = Role.builder().id(2L).name("OPERATOR").build();
        User existing = User.builder().id(1L).name("Old").email("old@test.com")
                .password("encoded").roles(Set.of(operator))
                .availability(UserAvailability.AVAILABLE).active(true).build();
        User updated = User.builder().name("New").email("new@test.com")
                .roles(Set.of(admin, operator))
                .availability(UserAvailability.ON_SITE).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.update(1L, updated);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getRoles()).containsExactlyInAnyOrder(admin, operator);
        assertThat(result.getAvailability()).isEqualTo(UserAvailability.ON_SITE);
        assertThat(result.getPassword()).isEqualTo("encoded");
    }

    @Test
    @DisplayName("S2: Update to an email owned by another user should be rejected")
    void shouldRejectDuplicateEmailOnUpdate() {
        User existing = User.builder().id(1L).name("Old").email("old@test.com").active(true).build();
        User updated = User.builder().email("taken@test.com").active(true).build();

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
}
