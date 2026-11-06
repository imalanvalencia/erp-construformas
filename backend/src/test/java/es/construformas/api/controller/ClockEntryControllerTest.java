package es.construformas.api.controller;

import es.construformas.api.model.ClockEntry;
import es.construformas.api.model.ClockType;
import es.construformas.api.model.Project;
import es.construformas.api.model.User;
import es.construformas.api.service.ClockEntryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClockEntryController.class)
@DisplayName("ClockEntryController Tests")
class ClockEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClockEntryService clockEntryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private ClockEntry sampleEntry() {
        User user = User.builder().id(1L).name("Worker").build();
        Project project = Project.builder().id(1L).name("Site A").build();
        return ClockEntry.builder()
                .id(1L)
                .user(user)
                .project(project)
                .clockType(ClockType.ENTRY)
                .userLatitude(40.4168)
                .userLongitude(-3.7038)
                .timestamp(LocalDateTime.of(2026, 1, 15, 8, 0))
                .build();
    }

    @Test
    @DisplayName("POST /api/clock-entries should accept the flat contract and echo it back")
    void registerShouldReturn201WithFlatContract() throws Exception {
        when(clockEntryService.register(any(ClockEntry.class))).thenReturn(sampleEntry());

        mockMvc.perform(post("/api/clock-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "projectId": 1,
                                  "clockType": "ENTRY",
                                  "userLatitude": 40.4168,
                                  "userLongitude": -3.7038,
                                  "timestamp": "2026-01-15T08:00:00",
                                  "notes": "Morning shift"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.userName").value("Worker"))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.projectName").value("Site A"))
                .andExpect(jsonPath("$.clockType").value("ENTRY"))
                .andExpect(jsonPath("$.userLatitude").value(40.4168))
                .andExpect(jsonPath("$.userLongitude").value(-3.7038))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.project").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/clock-entries without userId should clock in the authenticated user")
    void registerWithoutUserIdShouldReturn201() throws Exception {
        when(clockEntryService.register(any(ClockEntry.class))).thenReturn(sampleEntry());

        mockMvc.perform(post("/api/clock-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": 1,
                                  "clockType": "EXIT",
                                  "userLatitude": 40.4168,
                                  "userLongitude": -3.7038
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(clockEntryService).register(argThat(entry -> entry.getUser() == null));
    }

    @Test
    @DisplayName("GET /api/clock-entries/{id} should return the flat contract")
    void getByIdShouldReturnFlatContract() throws Exception {
        when(clockEntryService.findById(1L)).thenReturn(sampleEntry());

        mockMvc.perform(get("/api/clock-entries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clockType").value("ENTRY"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.userName").value("Worker"))
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.projectName").value("Site A"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.project").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/clock-entries/user/{userId} with date range should return entries")
    void getByUserShouldReturnEntries() throws Exception {
        when(clockEntryService.findByUserAndDateRange(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/clock-entries/user/1")
                        .param("start", "2026-01-01T00:00:00")
                        .param("end", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].clockType").value("ENTRY"))
                .andExpect(jsonPath("$[0].userName").value("Worker"))
                .andExpect(jsonPath("$[0].projectName").value("Site A"))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/clock-entries/{id} should return 204")
    void deleteEntryShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/clock-entries/1"))
                .andExpect(status().isNoContent());

        verify(clockEntryService).delete(1L);
    }

    @Test
    @DisplayName("S7: GET /api/clock-entries/me should return the caller's entries")
    void getMineShouldReturnEntries() throws Exception {
        when(clockEntryService.findByCallerAndDateRange(
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/clock-entries/me")
                        .param("start", "2026-01-01T00:00:00")
                        .param("end", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(1))
                .andExpect(jsonPath("$[0].userName").value("Worker"))
                .andExpect(jsonPath("$[0].user").doesNotExist());
    }
}
