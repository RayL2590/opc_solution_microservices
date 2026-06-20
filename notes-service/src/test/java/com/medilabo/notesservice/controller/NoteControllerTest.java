package com.medilabo.notesservice.controller;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.exception.GlobalExceptionHandler;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
}
