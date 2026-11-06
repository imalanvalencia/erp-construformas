package es.construformas.api.service;

import es.construformas.api.exception.UserNotFoundException;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
import es.construformas.api.model.Project;
import es.construformas.api.model.Role;
import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.repository.RoleRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

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

    public User update(Long id, User updated) {
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
        if (updated.getAvailability() != null) existing.setAvailability(updated.getAvailability());
        existing.setActive(updated.isActive());
        return userRepository.save(existing);
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
