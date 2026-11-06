package es.construformas.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of {@code PATCH /api/users/{id}/active} (S9).
 *
 * <p>The flag is required and boxed: an absent value must be a 400 rather than the
 * implicit {@code false} a primitive would default to, which would deactivate the user
 * by omission.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserActiveRequest {
    @NotNull
    private Boolean active;
}
