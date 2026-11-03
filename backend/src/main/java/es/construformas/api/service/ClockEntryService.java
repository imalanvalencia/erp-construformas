package es.construformas.api.service;

import es.construformas.api.model.ClockEntry;
import es.construformas.api.model.ClockType;
import es.construformas.api.model.Project;
import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.repository.ClockEntryRepository;
import es.construformas.api.repository.ProjectRepository;
import es.construformas.api.repository.UserRepository;
import es.construformas.api.security.SecurityUtils;
import es.construformas.api.util.HaversineUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ClockEntryService {

    private final ClockEntryRepository clockEntryRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    public ClockEntry register(ClockEntry entry) {
        User caller = SecurityUtils.getCurrentUser(userRepository);
        User user = SecurityUtils.resolveTargetUser(userRepository, caller,
                entry.getUser() != null ? entry.getUser().getId() : null);
        Project project = projectRepository.findById(entry.getProject().getId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!HaversineUtil.isWithinRadius(
                entry.getUserLatitude(), entry.getUserLongitude(),
                project.getLatitude(), project.getLongitude(),
                project.getAllowedRadiusMeters())) {
            throw new IllegalArgumentException("User is outside the allowed radius for this project");
        }

        entry.setUser(user);
        entry.setProject(project);
        if (entry.getTimestamp() == null) entry.setTimestamp(LocalDateTime.now());

        applyAutomaticAvailability(user, entry.getClockType());

        return clockEntryRepository.save(entry);
    }

    /**
     * S5: availability is derived from clock events and is never set by hand — an ENTRY
     * means the user is on site, an EXIT means it is available again. Only a real change
     * is written, so an unchanged availability costs no extra UPDATE.
     */
    private void applyAutomaticAvailability(User user, ClockType clockType) {
        UserAvailability target = switch (clockType) {
            case ENTRY -> UserAvailability.ON_SITE;
            case EXIT -> UserAvailability.AVAILABLE;
        };
        if (user.getAvailability() != target) {
            user.setAvailability(target);
            userRepository.save(user);
        }
    }

    public ClockEntry findById(Long id) {
        return clockEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Clock entry not found"));
    }

    public List<ClockEntry> findAll() {
        if (SecurityUtils.hasRole("OPERATOR")) {
            User user = SecurityUtils.getCurrentUser(userRepository);
            return clockEntryRepository.findByUserIdOrderByTimestampDesc(user.getId());
        }
        return clockEntryRepository.findAll();
    }

    public List<ClockEntry> findByUserAndDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        if (SecurityUtils.hasRole("OPERATOR")) {
            User user = SecurityUtils.getCurrentUser(userRepository);
            if (!user.getId().equals(userId)) {
                throw new IllegalArgumentException("Access denied: cannot view other users' entries");
            }
        }
        return clockEntryRepository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, start, end);
    }

    /**
     * S7: the authenticated caller's own entries in a range. The caller is resolved from
     * the security context, so the caller id is never taken from the URL.
     */
    public List<ClockEntry> findByCallerAndDateRange(LocalDateTime start, LocalDateTime end) {
        User caller = SecurityUtils.getCurrentUser(userRepository);
        return findByUserAndDateRange(caller.getId(), start, end);
    }

    public List<ClockEntry> findByProjectAndDateRange(Long projectId, LocalDateTime start, LocalDateTime end) {
        if (SecurityUtils.hasRole("OPERATOR")) {
            User user = SecurityUtils.getCurrentUser(userRepository);
            if (!projectRepository.existsByProjectIdAndOperatorId(projectId, user.getId())) {
                throw new IllegalArgumentException("Access denied: not assigned to this project");
            }
        }
        return clockEntryRepository.findByProjectIdAndTimestampBetween(projectId, start, end);
    }

    public void delete(Long id) {
        clockEntryRepository.deleteById(id);
    }
}
