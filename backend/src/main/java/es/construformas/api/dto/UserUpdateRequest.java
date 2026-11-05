package es.construformas.api.dto;

import es.construformas.api.model.Role;
import es.construformas.api.model.UserAvailability;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Admin edit accepted by {@code PUT /api/users/{id}}.
 *
 * <p>{@code active} is a boxed {@link Boolean}: the previous payload was the {@code User}
 * entity, whose primitive {@code boolean} made an omitted field deserialize to
 * {@code false}, so saving an unrelated edit silently deactivated the account (S9). A
 * null value now keeps the stored flag; an explicit value is applied through the same
 * guards as {@code PATCH /{id}/active}.
 *
 * <p>{@code availability} exists only so the admin modal's legacy payload keeps
 * deserializing. {@code update()} never reads it — availability is derived from clock
 * events alone (S5) — and a test pins that it stays untouched.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {
    private String name;
    private String email;
    private String phone;
    private String nie;
    private Set<Role> roles;
    private Boolean active;
    /** Accepted, deliberately never applied. See the class javadoc (S5). */
    private UserAvailability availability;
}
