package es.construformas.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One live session of the caller, returned by {@code GET /api/users/me/sessions} (S8).
 *
 * <p>The raw refresh token value never leaves the backend: only its opaque id and its
 * lifetime are exposed, so a leaked profile response cannot be replayed as a session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionDTO {
    private String id;
    private LocalDateTime createdAt;
    private LocalDateTime expiry;
}
