package es.construformas.api.service;

import es.construformas.api.exception.UserNotFoundException;
import es.construformas.api.dto.ChangePasswordRequest;
import es.construformas.api.dto.SessionDTO;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
import es.construformas.api.dto.UserUpdateRequest;
import es.construformas.api.model.Project;
import es.construformas.api.model.RefreshToken;
import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.repository.ProjectRepository;
import es.construformas.api.repository.RefreshTokenRepository;
import es.construformas.api.repository.RoleRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;

    /** Unambiguous alphabet: no 0/O, 1/l/I, so a temp password can be read aloud. */
    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    public User create(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (user.getNie() != null && userRepository.existsByNie(user.getNie())) {
            throw new IllegalArgumentException("NIE already exists");
        }
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            Role operatorRole = roleRepository.findByName("OPERATOR")
                    .orElseThrow(() -> new RuntimeException("OPERATOR role not found"));
            user.setRoles(Set.of(operatorRole));
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Resolve a user on behalf of the authenticated caller (S3). An OPERATOR may only
     * read its own record; an ADMIN may read any record.
     */
    public User findByIdForCaller(Long id) {
        User caller = SecurityUtils.getCurrentUser(userRepository);
        if (!isAdmin(caller) && !caller.getId().equals(id)) {
            throw new AccessDeniedException("Operators may only read their own record");
        }
        return findById(id);
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getName()));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> findByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    /**
     * Admin edit (S9). The payload carries a boxed {@link Boolean} so an omitted
     * {@code active} keeps the stored flag instead of silently deactivating the user;
     * an explicit change runs through the same guards as {@link #setActive}.
     * Availability is never read here (S5): it is derived from clock events alone.
     */
    public User update(Long id, UserUpdateRequest updated) {
        User existing = findById(id);

        String newEmail = updated.getEmail();
        if (newEmail != null && !newEmail.equals(existing.getEmail())) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email already exists");
            }
            existing.setEmail(newEmail);
        }

        if (updated.getName() != null) existing.setName(updated.getName());
        if (updated.getPhone() != null) existing.setPhone(updated.getPhone());
        if (updated.getNie() != null) existing.setNie(updated.getNie());
        if (updated.getRoles() != null && !updated.getRoles().isEmpty()) {
            existing.setRoles(updated.getRoles());
        }
        Boolean active = updated.getActive();
        if (active != null && active != existing.isActive()) {
            applyActive(existing, active);
        }
        return userRepository.save(existing);
    }

    /** S9: the dedicated activate/deactivate action, guarded the same way as a PUT. */
    public User setActive(Long id, boolean active) {
        User target = findById(id);
        if (target.isActive() != active) {
            applyActive(target, active);
        }
        return userRepository.save(target);
    }

    /**
     * S9/S12: flipping a flag off must never leave the system without an administrator,
     * and must never let an account switch itself off. Both refusals are conflicts (409),
     * because the request is well-formed and only the resulting state is not allowed.
     */
    private void applyActive(User target, boolean active) {
        if (active) {
            target.setActive(true);
            return;
        }
        User caller = SecurityUtils.getCurrentUser(userRepository);
        if (caller.getId().equals(target.getId())) {
            throw new IllegalStateException("You cannot deactivate your own account");
        }
        assertNotLastActiveAdmin(target);
        target.setActive(false);
    }

    private void assertNotLastActiveAdmin(User target) {
        if (isAdmin(target) && userRepository.countActiveAdmins() <= 1) {
            throw new IllegalStateException("Cannot remove the last active administrator");
        }
    }

    /**
     * S10: hand out one temporary password, force a change on the next login and kill
     * every live session so the old credentials stop working immediately. The plain
     * value is returned exactly once and never stored.
     */
    public String resetPassword(Long id) {
        User target = findById(id);
        String temporaryPassword = generateTemporaryPassword();
        target.setPassword(passwordEncoder.encode(temporaryPassword));
        target.setMustChangePassword(true);
        userRepository.save(target);
        revokeAllSessions(target);
        return temporaryPassword;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            password.append(TEMP_PASSWORD_ALPHABET.charAt(RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }

    /**
     * S11: an ADMIN may point any user at any project. The project must exist; the
     * assignment itself is unrestricted because an ADMIN is not bound to
     * {@code project_operators}.
     */
    public User setCurrentProject(Long userId, Long projectId) {
        User target = findById(userId);
        target.setCurrentProject(requireProject(projectId));
        return userRepository.save(target);
    }

    /**
     * S6: an OPERATOR switches its own current project, but only among the projects it
     * is assigned to in {@code project_operators} — anything else is a 403, not a 404,
     * so the endpoint never confirms the existence of projects the caller cannot see.
     */
    public UserProfileDTO switchMyCurrentProject(Long projectId) {
        User caller = loadCaller();
        Project project = requireProject(projectId);
        if (!projectRepository.existsByProjectIdAndOperatorId(projectId, caller.getId())) {
            throw new AccessDeniedException("You are not assigned to this project");
        }
        caller.setCurrentProject(project);
        return toProfile(userRepository.save(caller));
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * The authenticated caller's own profile (S2). The caller comes from the security
     * context, never from a path variable, so an OPERATOR can only ever read itself.
     */
    public UserProfileDTO getProfile() {
        return toProfile(loadCaller());
    }

    /**
     * Self-service contact edit (S3): only phone and nie. Name, email, roles, active and
     * availability are an ADMIN's to change. A null value keeps the current one, a blank
     * value clears it.
     */
    public UserProfileDTO updateProfile(UserProfileUpdateRequest request) {
        User caller = loadCaller();

        String phone = request.getPhone();
        if (phone != null) {
            phone = phone.isBlank() ? null : phone;
            assertContactIsFree(caller, phone, null);
            caller.setPhone(phone);
        }

        String nie = request.getNie();
        if (nie != null) {
            nie = nie.isBlank() ? null : nie;
            assertContactIsFree(caller, null, nie);
            caller.setNie(nie);
        }

        return toProfile(userRepository.save(caller));
    }

    /** Both columns are UNIQUE, so a duplicate must surface as 400, not as a DB 500. */
    private void assertContactIsFree(User caller, String phone, String nie) {
        if (phone != null && !phone.equals(caller.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone already exists");
        }
        if (nie != null && !nie.equals(caller.getNie()) && userRepository.existsByNie(nie)) {
            throw new IllegalArgumentException("NIE already exists");
        }
    }

    private User loadCaller() {
        String email = SecurityUtils.getCurrentUserEmail();
        return userRepository.findWithCurrentProjectByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
    }

    private UserProfileDTO toProfile(User user) {
        Project project = user.getCurrentProject();
        return UserProfileDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .nie(user.getNie())
                .roles(user.getRoles())
                .availability(user.getAvailability())
                .currentProjectId(project != null ? project.getId() : null)
                .currentProjectName(project != null ? project.getName() : null)
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * S4: the caller swaps its own password. The current one must match, the new one is
     * encoded, every refresh token is revoked (all sessions die) and a pending
     * {@code must_change_password} flag left by an admin reset is cleared.
     */
    public void changeMyPassword(ChangePasswordRequest request) {
        User caller = loadCaller();
        if (!passwordEncoder.matches(request.getCurrentPassword(), caller.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        caller.setPassword(passwordEncoder.encode(request.getNewPassword()));
        caller.setMustChangePassword(false);
        userRepository.save(caller);
        revokeAllSessions(caller);
    }

    /** S8: the caller's live sessions, without the token values. */
    public List<SessionDTO> listSessions() {
        User caller = loadCaller();
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findByUserIdAndRevokedFalse(caller.getId()).stream()
                .filter(token -> token.getExpiry().isAfter(now))
                .sorted(Comparator.comparing(RefreshToken::getCreatedAt).reversed())
                .map(token -> SessionDTO.builder()
                        .id(token.getId())
                        .createdAt(token.getCreatedAt())
                        .expiry(token.getExpiry())
                        .build())
                .toList();
    }

    /**
     * S8: revoke every session but the one the caller is asking from, so "close the
     * others" never logs the current device out. A token that is not the caller's own
     * is refused instead of silently revoking everything.
     */
    public void closeOtherSessions(String currentRefreshToken) {
        User caller = loadCaller();
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(caller.getId());
        boolean ownsCurrent = tokens.stream()
                .anyMatch(token -> token.getToken().equals(currentRefreshToken));
        if (!ownsCurrent) {
            throw new IllegalArgumentException("Refresh token not found");
        }
        tokens.stream()
                .filter(token -> !token.getToken().equals(currentRefreshToken))
                .filter(token -> !token.isRevoked())
                .forEach(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private void revokeAllSessions(User user) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId()).forEach(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public UserAvailability getAvailability(Long id) {
        return findById(id).getAvailability();
    }

    public UserAvailability updateAvailability(Long id, UserAvailability availability) {
        User user = findById(id);
        user.setAvailability(availability);
        userRepository.save(user);
        return availability;
    }
}
