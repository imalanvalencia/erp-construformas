package es.construformas.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Self-service contact edit accepted by {@code PUT /api/users/me}.
 *
 * <p>Only {@code phone} and {@code nie} are editable by their owner: {@code name},
 * {@code email}, {@code roles}, {@code active} and {@code availability} belong to an
 * ADMIN. {@code null} keeps the current value; an empty string clears it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {
    private String phone;
    private String nie;
}
