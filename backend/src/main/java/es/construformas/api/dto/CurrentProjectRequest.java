package es.construformas.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PUT /api/users/{id}/current-project} and
 * {@code PUT /api/users/me/current-project} (S6, S11).
 *
 * <p>The project is required: assignment is the only operation here, so an empty body
 * is a 400 rather than an implicit "clear the current project".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentProjectRequest {
    @NotNull
    private Long projectId;
}
