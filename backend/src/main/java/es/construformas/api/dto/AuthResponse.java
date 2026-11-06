package es.construformas.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private Long id;
    private String accessToken;
    private String refreshToken;
    private String email;
    private List<String> roles;
    private String name;
    /** True while an admin reset forces the user to pick a new password (S10). */
    private boolean mustChangePassword;
}
