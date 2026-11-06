package es.construformas.api.controller;

import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.model.Role;
import es.construformas.api.dto.ChangePasswordRequest;
import es.construformas.api.dto.SessionDTO;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
import es.construformas.api.dto.UserUpdateRequest;
import es.construformas.api.exception.UserNotFoundException;
import es.construformas.api.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import es.construformas.api.security.JwtUtil;
import es.construformas.api.security.CustomUserDetailsService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private User sampleUser() {
        return User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@test.com")
                .password("raw-secret")
                .role(UserRole.OPERATOR)
                .active(true)
                .build();
    }

    private UserProfileDTO sampleProfile() {
        return UserProfileDTO.builder()
                .id(1L)
                .name("John Doe")
                .email("john@test.com")
                .phone("555-1234")
                .nie("X123")
                .roles(Set.of(Role.builder().id(1L).name("ADMIN").build()))
                .availability(UserAvailability.AVAILABLE)
                .active(true)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    @Test
    @DisplayName("S1: POST /api/users should never return the password field")
    void createShouldNotLeakPassword() throws Exception {
        when(userService.create(any(User.class))).thenReturn(sampleUser());

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUser())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("S1: GET /api/users should never return the password field")
    void getAllShouldNotLeakPassword() throws Exception {
        when(userService.findAll()).thenReturn(List.of(sampleUser()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }

    @Test
    @DisplayName("S1: GET /api/users/{id} should never return the password field")
    void getByIdShouldNotLeakPassword() throws Exception {
        when(userService.findByIdForCaller(1L)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("S1: PUT /api/users/{id} should never return the password field")
    void updateShouldNotLeakPassword() throws Exception {
        when(userService.update(eq(1L), any(UserUpdateRequest.class))).thenReturn(sampleUser());

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Doe\",\"email\":\"john@test.com\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("S3: GET /api/users/{id} should return 404 for an unknown id")
    void getByIdShouldReturn404WhenNotFound() throws Exception {
        when(userService.findByIdForCaller(99L))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(get("/api/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/users should create and return user")
    void createUserShouldReturn201() throws Exception {
        User user = sampleUser();
        when(userService.create(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@test.com"));
    }

    @Test
    @DisplayName("GET /api/users/{id} should return user")
    void getByIdShouldReturnUser() throws Exception {
        when(userService.findByIdForCaller(1L)).thenReturn(sampleUser());

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("John Doe"));
    }

    @Test
    @DisplayName("GET /api/users should return all users")
    void getAllShouldReturnUsers() throws Exception {
        when(userService.findAll()).thenReturn(List.of(sampleUser()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }

    @Test
    @DisplayName("GET /api/users/role/{role} should return users by role")
    void getByRoleShouldReturnUsers() throws Exception {
        when(userService.findByRole(UserRole.ADMIN)).thenReturn(List.of(sampleUser()));

        mockMvc.perform(get("/api/users/role/ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].role").value("OPERATOR"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} should update user")
    void updateUserShouldReturnUpdated() throws Exception {
        User updated = User.builder().name("Jane Doe").phone("555-1234").active(true).build();
        when(userService.update(eq(1L), any(UserUpdateRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"phone\":\"555-1234\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    @DisplayName("S5: PUT /api/users/{id} still accepts a body carrying legacy fields")
    void updateShouldTolerateLegacyBodyFields() throws Exception {
        when(userService.update(eq(1L), any(UserUpdateRequest.class))).thenReturn(sampleUser());

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"John Doe\",\"email\":\"john@test.com\","
                                + "\"availability\":\"ON_SITE\",\"currentProjectId\":5,"
                                + "\"createdAt\":\"2026-01-01T00:00:00\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} should return 204")
    void deleteUserShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
    }

    @Test
    @DisplayName("S12: DELETE /api/users/{id} should return 409 for a user with history")
    void deleteUserShouldReturn409WithHistory() throws Exception {
        doThrow(new IllegalStateException(
                "User has activity history; deactivate the account instead"))
                .when(userService).delete(3L);

        mockMvc.perform(delete("/api/users/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("User has activity history; deactivate the account instead"));
    }

    @Test
    @DisplayName("S12: DELETE /api/users/{id} should return 409 on self-deletion")
    void deleteUserShouldReturn409OnSelfDeletion() throws Exception {
        doThrow(new IllegalStateException("You cannot delete your own account"))
                .when(userService).delete(9L);

        mockMvc.perform(delete("/api/users/9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You cannot delete your own account"));
    }

    @Test
    @DisplayName("GET /api/users/{id}/availability should return availability")
    void getAvailabilityShouldReturn() throws Exception {
        when(userService.getAvailability(1L)).thenReturn(UserAvailability.ON_SITE);

        mockMvc.perform(get("/api/users/1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("ON_SITE"));
    }

    @Test
    @DisplayName("PUT /api/users/{id}/availability should update availability")
    void updateAvailabilityShouldReturn() throws Exception {
        when(userService.updateAvailability(eq(1L), any(UserAvailability.class)))
                .thenReturn(UserAvailability.UNAVAILABLE);

        mockMvc.perform(put("/api/users/1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"UNAVAILABLE\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("UNAVAILABLE"));
    }

    @Test
    @DisplayName("S2: GET /api/users/me should return the caller profile without the password")
    void getProfileShouldNotLeakPassword() throws Exception {
        when(userService.getProfile()).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@test.com"))
                .andExpect(jsonPath("$.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.roles[0].name").value("ADMIN"));
    }

    @Test
    @DisplayName("S3: PUT /api/users/me should apply phone and nie")
    void updateProfileShouldApplyContactFields() throws Exception {
        when(userService.updateProfile(any(UserProfileUpdateRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"555-1234\",\"nie\":\"X123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("555-1234"))
                .andExpect(jsonPath("$.nie").value("X123"));
    }

    @Test
    @DisplayName("S4: POST /api/users/me/password should return 204")
    void changePasswordShouldReturn204() throws Exception {
        doNothing().when(userService).changeMyPassword(any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("S4: POST /api/users/me/password should return 400 on a wrong current password")
    void changePasswordShouldReturn400OnWrongCurrent() throws Exception {
        doThrow(new IllegalArgumentException("Current password is incorrect"))
                .when(userService).changeMyPassword(any(ChangePasswordRequest.class));

        mockMvc.perform(post("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Current password is incorrect"));
    }

    @Test
    @DisplayName("S8: GET /api/users/me/sessions should list sessions without the token value")
    void listSessionsShouldReturnSafeFields() throws Exception {
        when(userService.listSessions()).thenReturn(List.of(
                SessionDTO.builder()
                        .id("sess-1")
                        .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                        .expiry(LocalDateTime.of(2026, 1, 15, 10, 0))
                        .build()));

        mockMvc.perform(get("/api/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("sess-1"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].expiry").exists())
                .andExpect(jsonPath("$[0].token").doesNotExist());
    }

    @Test
    @DisplayName("S8: DELETE /api/users/me/sessions should close the other sessions")
    void closeSessionsShouldReturn204() throws Exception {
        doNothing().when(userService).closeOtherSessions("current-refresh");

        mockMvc.perform(delete("/api/users/me/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"current-refresh\"}"))
                .andExpect(status().isNoContent());

        verify(userService).closeOtherSessions("current-refresh");
    }

    // ------------------------------------------------------- S9: activate / deactivate

    @Test
    @DisplayName("S9: PATCH /api/users/{id}/active should return the updated user")
    void setActiveShouldReturnUser() throws Exception {
        when(userService.setActive(1L, false)).thenReturn(sampleUser());

        mockMvc.perform(patch("/api/users/1/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).setActive(1L, false);
    }

    @Test
    @DisplayName("S9: PATCH /api/users/{id}/active should return 409 when a guard refuses")
    void setActiveShouldReturn409OnGuard() throws Exception {
        when(userService.setActive(1L, false))
                .thenThrow(new IllegalStateException("You cannot deactivate your own account"));

        mockMvc.perform(patch("/api/users/1/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You cannot deactivate your own account"));
    }

    @Test
    @DisplayName("S9: PATCH /api/users/{id}/active should return 400 when the flag is missing")
    void setActiveShouldReturn400WithoutFlag() throws Exception {
        mockMvc.perform(patch("/api/users/1/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).setActive(any(), any(Boolean.class));
    }

    // -------------------------------------------------------------- S10: password reset

    @Test
    @DisplayName("S10: POST /api/users/{id}/password-reset should return the password once")
    void passwordResetShouldReturnTheTemporaryPassword() throws Exception {
        when(userService.resetPassword(1L)).thenReturn("Temp-2026-xkcd");

        mockMvc.perform(post("/api/users/1/password-reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("Temp-2026-xkcd"));
    }

    @Test
    @DisplayName("S10: POST /api/users/{id}/password-reset should return 404 for an unknown user")
    void passwordResetShouldReturn404ForUnknownUser() throws Exception {
        when(userService.resetPassword(99L)).thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(post("/api/users/99/password-reset"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------ S11 / S6: current project

    @Test
    @DisplayName("S11: PUT /api/users/{id}/current-project should return the updated user")
    void setCurrentProjectShouldReturnUser() throws Exception {
        User assigned = User.builder().id(1L).name("John Doe").active(true).build();
        when(userService.setCurrentProject(1L, 7L)).thenReturn(assigned);

        mockMvc.perform(put("/api/users/1/current-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).setCurrentProject(1L, 7L);
    }

    @Test
    @DisplayName("S11: PUT /api/users/{id}/current-project should return 400 for an unknown project")
    void setCurrentProjectShouldReturn400ForUnknownProject() throws Exception {
        when(userService.setCurrentProject(1L, 404L))
                .thenThrow(new IllegalArgumentException("Project not found: 404"));

        mockMvc.perform(put("/api/users/1/current-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":404}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Project not found: 404"));
    }

    @Test
    @DisplayName("S6: PUT /api/users/me/current-project should return the refreshed profile")
    void switchMyCurrentProjectShouldReturnProfile() throws Exception {
        when(userService.switchMyCurrentProject(5L)).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/users/me/current-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.id").value(1));

        verify(userService).switchMyCurrentProject(5L);
    }

    @Test
    @DisplayName("S6: PUT /api/users/me/current-project should return 403 when not assigned")
    void switchMyCurrentProjectShouldReturn403WhenNotAssigned() throws Exception {
        when(userService.switchMyCurrentProject(5L))
                .thenThrow(new AccessDeniedException("You are not assigned to this project"));

        mockMvc.perform(put("/api/users/me/current-project")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":5}"))
                .andExpect(status().isForbidden());
    }
}
