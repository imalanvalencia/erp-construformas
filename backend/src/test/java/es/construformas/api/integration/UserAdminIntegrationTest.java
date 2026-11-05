package es.construformas.api.integration;

import es.construformas.api.model.ClockEntry;
import es.construformas.api.model.ClockType;
import es.construformas.api.model.Client;
import es.construformas.api.model.Project;
import es.construformas.api.model.ProjectStatus;
import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.repository.ClockEntryRepository;
import es.construformas.api.repository.ClientRepository;
import es.construformas.api.repository.ProjectRepository;
import es.construformas.api.repository.RoleRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S12: the delete guards against the real schema. The native {@code hasHistory}
 * query lists every non-cascading {@code users(id)} foreign key, so only an
 * integration test over the Hibernate-created tables proves it runs and stays in
 * step with them; the unit test can only prove the branching.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("User admin integration tests")
class UserAdminIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ClockEntryRepository clockEntryRepository;
    @Autowired private UserService userService;

    private Role adminRole;
    private Role operatorRole;
    private User root;

    @BeforeEach
    void setUp() {
        adminRole = roleRepository.save(Role.builder().name("ADMIN").build());
        operatorRole = roleRepository.save(Role.builder().name("OPERATOR").build());
        root = userRepository.save(User.builder()
                .name("Root Admin")
                .email("root@test.com")
                .password("hashed")
                .roles(Set.of(adminRole))
                .active(true)
                .build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User operator(String email) {
        return userRepository.save(User.builder()
                .name("Operator")
                .email(email)
                .password("hashed")
                .roles(Set.of(operatorRole))
                .active(true)
                .build());
    }

    private User userWithoutRoles(String email) {
        return userRepository.save(User.builder()
                .name("No Roles")
                .email(email)
                .password("hashed")
                .active(true)
                .build());
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private void clockIn(User user) {
        Client client = clientRepository.save(Client.builder().name("Client").build());
        Project project = projectRepository.save(Project.builder()
                .client(client)
                .name("Project")
                .address("Calle 1")
                .latitude(40.0)
                .longitude(-3.0)
                .status(ProjectStatus.PLANNED)
                .build());
        clockEntryRepository.save(ClockEntry.builder()
                .user(user)
                .project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.0)
                .userLongitude(-3.0)
                .build());
    }

    @Test
    @DisplayName("S12: hasHistory should be false for fresh accounts")
    void hasHistoryShouldBeFalseForFreshAccounts() {
        User fresh = operator("fresh@test.com");

        assertThat(userRepository.hasHistory(fresh.getId())).isFalse();
        assertThat(userRepository.hasHistory(root.getId())).isFalse();
    }

    @Test
    @DisplayName("S12: hasHistory should be true once the account clocked in")
    void hasHistoryShouldBeTrueAfterAClockIn() {
        User worker = operator("worker@test.com");
        clockIn(worker);

        assertThat(userRepository.hasHistory(worker.getId())).isTrue();
    }

    @Test
    @DisplayName("S12: countActiveAdmins should ignore an inactive administrator")
    void countActiveAdminsShouldIgnoreInactiveAdmins() {
        userRepository.save(User.builder()
                .name("Retired Admin")
                .email("retired@test.com")
                .password("hashed")
                .roles(Set.of(adminRole))
                .active(false)
                .build());

        assertThat(userRepository.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    @DisplayName("S12: delete should refuse an account with history and keep the row")
    void deleteShouldRefuseAnAccountWithHistory() {
        User worker = operator("worker@test.com");
        clockIn(worker);
        authenticateAs("root@test.com");

        assertThatThrownBy(() -> userService.delete(worker.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deactivate");

        userRepository.flush();
        assertThat(userRepository.existsById(worker.getId())).isTrue();
    }

    @Test
    @DisplayName("S12: delete should remove an account that owns nothing")
    void deleteShouldRemoveAnAccountWithoutHistory() {
        User nobody = userWithoutRoles("nobody@test.com");
        authenticateAs("root@test.com");

        userService.delete(nobody.getId());

        userRepository.flush();
        assertThat(userRepository.existsById(nobody.getId())).isFalse();
    }

    @Test
    @DisplayName("S12: delete should refuse the caller's own account")
    void deleteShouldRefuseSelfDeletion() {
        authenticateAs("root@test.com");

        assertThatThrownBy(() -> userService.delete(root.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("your own account");

        userRepository.flush();
        assertThat(userRepository.existsById(root.getId())).isTrue();
    }
}
