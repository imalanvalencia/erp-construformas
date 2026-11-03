package es.construformas.api.controller;

import es.construformas.api.dto.ClockEntryDTO;
import es.construformas.api.dto.ClockEntryMapper;
import es.construformas.api.model.ClockEntry;
import es.construformas.api.service.ClockEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/clock-entries")
@RequiredArgsConstructor
public class ClockEntryController {
    private final ClockEntryService clockEntryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ClockEntryDTO> register(@Valid @RequestBody ClockEntryDTO dto) {
        ClockEntry entry = clockEntryService.register(ClockEntryMapper.toEntity(dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(ClockEntryMapper.toDto(entry));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<ClockEntryDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ClockEntryMapper.toDto(clockEntryService.findById(id)));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<List<ClockEntryDTO>> getByUser(
            @PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(
                clockEntryService.findByUserAndDateRange(userId, start, end).stream()
                        .map(ClockEntryMapper::toDto)
                        .toList());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<List<ClockEntryDTO>> getMine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(
                clockEntryService.findByCallerAndDateRange(start, end).stream()
                        .map(ClockEntryMapper::toDto)
                        .toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        clockEntryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
