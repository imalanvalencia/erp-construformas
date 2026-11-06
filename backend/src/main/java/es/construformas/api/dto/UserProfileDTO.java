package es.construformas.api.dto;

import es.construformas.api.model.Role;
import es.construformas.api.model.UserAvailability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Self-scoped profile view returned by {@code GET /api/users/me}.
 *
 * <p>Deliberately a projection rather than the entity: the entity carries the lazy
 * {@code currentProject} graph (client, operators) and the deprecated transient
 * {@code role} echo, neither of which belongs in a profile payload. The password hash
 * never reaches this DTO at all.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String nie;
    private Set<Role> roles;
    private UserAvailability availability;
    private Long currentProjectId;
    private String currentProjectName;
    private boolean active;
    private LocalDateTime createdAt;
}
