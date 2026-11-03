package es.construformas.api.controller;

import es.construformas.api.model.User;
import es.construformas.api.model.UserAvailability;
import es.construformas.api.model.UserRole;
import es.construformas.api.model.Role;
import es.construformas.api.dto.UserProfileDTO;
import es.construformas.api.dto.UserProfileUpdateRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(userService.update(eq(1L), any(User.class))).thenReturn(sampleUser());

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUser())))
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
        when(userService.update(eq(1L), any(User.class))).thenReturn(updated);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    @DisplayName("DELETE /api/users/{id} should return 204")
    void deleteUserShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).delete(1L);
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
}
