package es.construformas.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code POST /api/users/{id}/password-reset} (S10).
 *
 * <p>The temporary password is shown exactly once: it is stored only as a BCrypt hash,
 * so this response is the only chance an administrator has to copy it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetResponse {
    private String temporaryPassword;
}
