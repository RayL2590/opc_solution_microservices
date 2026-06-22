package com.medilabo.notesservice.controller;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.exception.GlobalExceptionHandler;
import com.medilabo.notesservice.exception.NoteNotFoundException;
import com.medilabo.notesservice.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SecurityConfig absente en 3.2 + Mongo exclu : @WebMvcTest est DB-free par nature.
// MongoConfig (@EnableMongoAuditing) requiert mongoMappingContext absent dans le slice MVC.
// UserDetailsServiceAutoConfiguration exclu car il requiert SecurityProperties (lié à SecurityAutoConfiguration).
@WebMvcTest(value = NoteController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                MongoAutoConfiguration.class,
                DataMongoAutoConfiguration.class,
                DataMongoRepositoriesAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    @Test
    void addNote_validPayload_returns201() throws Exception {
        NoteDTO dto = new NoteDTO("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(noteService.addNote(any())).willReturn(dto);

        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"patient\":\"TestNone\",\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.patId").value(1));
    }

    @Test
    void addNote_missingPatId_returns400WithFieldError() throws Exception {
        // patId absent → @NotNull déclenche la validation
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.patId").value(
                        containsString("obligatoire")));
    }

    @Test
    void addNote_blankNote_returns400WithFieldError() throws Exception {
        // note vide → @NotBlank déclenche la validation
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").value(
                        containsString("vide")));
    }

    @Test
    void getNotesByPatId_withNotes_returns200WithArray() throws Exception {
        NoteDTO n1 = new NoteDTO("id2", 4, "TestVascular", "Note récente.", Instant.now());
        NoteDTO n2 = new NoteDTO("id1", 4, "TestVascular", "Note plus ancienne.", Instant.now().minusSeconds(60));
        given(noteService.getNotesByPatId(4)).willReturn(List.of(n1, n2));

        mockMvc.perform(get("/notes").param("patId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("id2"));
    }

    @Test
    void getNotesByPatId_noNotes_returns200WithEmptyArray() throws Exception {
        given(noteService.getNotesByPatId(999)).willReturn(List.of());

        mockMvc.perform(get("/notes").param("patId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getNoteById_existingId_returns200WithNoteDTO() throws Exception {
        NoteDTO dto = new NoteDTO("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(noteService.getNoteById("abc123")).willReturn(dto);

        mockMvc.perform(get("/notes/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.patId").value(1));
    }

    @Test
    void getNoteById_unknownId_returns404ProblemDetail() throws Exception {
        given(noteService.getNoteById("unknown")).willThrow(new NoteNotFoundException("unknown"));

        mockMvc.perform(get("/notes/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("unknown")));
    }

    @Test
    void getNotesByPatId_missingPatId_returns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/notes"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNotesByPatId_nonNumericPatId_returns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/notes").param("patId", "abc"))
                .andExpect(status().isBadRequest());
    }
}
