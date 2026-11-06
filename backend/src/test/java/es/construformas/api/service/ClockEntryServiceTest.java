package es.construformas.api.service;

import es.construformas.api.model.*;
import es.construformas.api.repository.ClockEntryRepository;
import es.construformas.api.repository.ProjectRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
class ClockEntryServiceTest {

    @Mock private ClockEntryRepository clockEntryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @InjectMocks private ClockEntryService clockEntryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Puts a user in the security context, mirroring what JwtAuthFilter does at runtime. */
    private User authenticate(User user, String roleName) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + roleName))));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    @DisplayName("Register without a userId should clock in the authenticated caller")
    void shouldClockInForCallerWhenUserIdOmitted() {
        User caller = authenticate(
                User.builder().id(1L).email("worker@test.com").build(), "OPERATOR");
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(null).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0001).userLongitude(-3.0).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(clockEntryRepository.save(any(ClockEntry.class))).thenAnswer(i -> i.getArgument(0));

        ClockEntry result = clockEntryService.register(entry);

        assertThat(result.getUser()).isEqualTo(caller);
    }

    @Test
    @DisplayName("An OPERATOR clocking in for another user should be denied")
    void shouldRejectOperatorClockingForAnotherUser() {
        authenticate(User.builder().id(1L).email("worker@test.com").build(), "OPERATOR");
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(User.builder().id(2L).build()).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0001).userLongitude(-3.0).build();

        assertThatThrownBy(() -> clockEntryService.register(entry))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("themselves");
        verify(clockEntryRepository, never()).save(any(ClockEntry.class));
    }

    @Test
    @DisplayName("An ADMIN should be allowed to clock in for another user")
    void shouldLetAdminClockInForAnotherUser() {
        authenticate(User.builder().id(1L).email("admin@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build(), "ADMIN");
        User other = User.builder().id(2L).email("other@test.com").build();
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(other).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0001).userLongitude(-3.0).build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(other));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(clockEntryRepository.save(any(ClockEntry.class))).thenAnswer(i -> i.getArgument(0));

        ClockEntry result = clockEntryService.register(entry);

        assertThat(result.getUser()).isEqualTo(other);
    }

    @Test
    @DisplayName("Register clock entry within radius should succeed")
    void shouldRegisterClockEntrySuccessfully() {
        User user = authenticate(
                User.builder().id(1L).email("worker@test.com").build(), "OPERATOR");
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(user).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0001).userLongitude(-3.0).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(clockEntryRepository.save(any(ClockEntry.class))).thenAnswer(i -> i.getArgument(0));

        ClockEntry result = clockEntryService.register(entry);

        assertThat(result.getTimestamp()).isNotNull();
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getProject()).isEqualTo(project);
    }

    @Test
    @DisplayName("Register clock entry outside radius should throw")
    void shouldRejectClockEntryOutsideRadius() {
        User user = authenticate(
                User.builder().id(1L).email("worker@test.com").build(), "OPERATOR");
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(user).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(48.8566).userLongitude(2.3522).build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> clockEntryService.register(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the allowed radius");
    }

    @Test
    @DisplayName("Register with non-existent user should throw")
    void shouldRejectNonExistentUser() {
        authenticate(User.builder().id(1L).email("admin@test.com")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build())).build(), "ADMIN");
        Project project = Project.builder()
                .id(1L).latitude(40.0).longitude(-3.0).allowedRadiusMeters(50).build();
        ClockEntry entry = ClockEntry.builder()
                .user(User.builder().id(99L).build()).project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0).userLongitude(-3.0).build();

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clockEntryService.register(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("Register with non-existent project should throw")
    void shouldRejectNonExistentProject() {
        User user = authenticate(
                User.builder().id(1L).email("worker@test.com").build(), "OPERATOR");
        ClockEntry entry = ClockEntry.builder()
                .user(user).project(Project.builder().id(99L).build())
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0).userLongitude(-3.0).build();

        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clockEntryService.register(entry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Project not found");
    }

    @Test
    @DisplayName("Find by ID should return entry when exists")
    void shouldFindById() {
        ClockEntry entry = ClockEntry.builder().id(1L).clockType(ClockType.ENTRY).build();
        when(clockEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        ClockEntry result = clockEntryService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Find by ID should throw when not found")
    void shouldThrowWhenNotFound() {
        when(clockEntryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clockEntryService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Clock entry not found");
    }

    @Test
    @DisplayName("Find by user and date range should delegate to repository")
    void shouldFindByUserAndDateRange() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);
        when(clockEntryRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(1L, start, end))
                .thenReturn(List.of(ClockEntry.builder().id(1L).build()));

        var result = clockEntryService.findByUserAndDateRange(1L, start, end);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Delete should delegate to repository")
    void shouldDeleteEntry() {
        clockEntryService.delete(1L);
        verify(clockEntryRepository).deleteById(1L);
    }

    @Test
    @DisplayName("S7: findByCallerAndDateRange should resolve the caller and scope to it")
    void shouldFindByCallerAndDateRange() {
        User caller = authenticate(
                User.builder().id(5L).email("op@test.com").build(), "OPERATOR");
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);
        when(clockEntryRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(5L, start, end))
                .thenReturn(List.of(ClockEntry.builder().id(1L).user(caller).build()));

        var result = clockEntryService.findByCallerAndDateRange(start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser()).isEqualTo(caller);
    }
}
