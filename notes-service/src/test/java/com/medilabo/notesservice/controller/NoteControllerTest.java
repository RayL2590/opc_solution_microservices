package com.medilabo.notesservice.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.medilabo.notesservice.config.SecurityConfig;
import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.exception.GlobalExceptionHandler;
import com.medilabo.notesservice.exception.NoteNotFoundException;
import com.medilabo.notesservice.service.NoteService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// On importe la vraie SecurityConfig pour exercer la chaîne HTTP Basic, mais Mongo reste exclu :
// @WebMvcTest doit rester sans DB. MongoConfig (@EnableMongoAuditing) a besoin de mongoMappingContext,
// qui n'existe pas dans ce slice.
@WebMvcTest(value = NoteController.class,
        excludeAutoConfiguration = {
                MongoAutoConfiguration.class,
                DataMongoAutoConfiguration.class,
                DataMongoRepositoriesAutoConfiguration.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    @Test
    void everyEndpoint_emitsADebugLog() throws Exception {
        Logger controllerLogger = (Logger) LoggerFactory.getLogger(NoteController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = controllerLogger.getLevel();
        controllerLogger.setLevel(Level.DEBUG);
        controllerLogger.addAppender(appender);
        try {
            NoteDTO dto = new NoteDTO("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
            given(noteService.addNote(any())).willReturn(dto);
            given(noteService.getNotesByPatId(1)).willReturn(List.of(dto));
            given(noteService.getNoteById("abc123")).willReturn(dto);

            mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"patId\":1,\"patient\":\"TestNone\",\"note\":\"Observation clinique.\"}"))
                    .andExpect(status().isCreated());
            mockMvc.perform(get("/notes").with(httpBasic("medilabo", "medilabo123")).param("patId", "1"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/notes/abc123").with(httpBasic("medilabo", "medilabo123")))
                    .andExpect(status().isOk());

            List<String> debugMessages = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.DEBUG)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(debugMessages).containsExactly(
                    "POST /notes patId=1",
                    "GET /notes patId=1",
                    "GET /notes/abc123");
        } finally {
            controllerLogger.detachAppender(appender);
            controllerLogger.setLevel(previousLevel);
        }
    }

    @Test
    void addNote_validPayload_returns201() throws Exception {
        NoteDTO dto = new NoteDTO("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(noteService.addNote(any())).willReturn(dto);

        mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"patient\":\"TestNone\",\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.patId").value(1));
    }

    @Test
    void addNote_missingPatId_returns400WithFieldError() throws Exception {
        // patId absent → @NotNull
        mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.patId[0]").value(
                        containsString("obligatoire")));
    }

    @Test
    void addNote_blankNote_returns400WithFieldError() throws Exception {
        // note vide → @NotBlank
        mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"note\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note[0]").value(
                        containsString("vide")));
    }

    @Test
    void addNote_blankPatient_returns400WithFieldError() throws Exception {
        // patient vide → @NotBlank : le nom est dénormalisé sur chaque note (D-DATA-3), une
        // note sans lui est illisible dans la timeline.
        mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"patient\":\"  \",\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.patient[0]").value(
                        containsString("obligatoire")));
    }

    @Test
    void addNote_missingPatient_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/notes").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.patient[0]").value(
                        containsString("obligatoire")));
    }

    @Test
    void getNotesByPatId_withNotes_returns200WithArray() throws Exception {
        NoteDTO n1 = new NoteDTO("id2", 4, "TestVascular", "Note récente.", Instant.now());
        NoteDTO n2 = new NoteDTO("id1", 4, "TestVascular", "Note plus ancienne.", Instant.now().minusSeconds(60));
        given(noteService.getNotesByPatId(4)).willReturn(List.of(n1, n2));

        mockMvc.perform(get("/notes").with(httpBasic("medilabo", "medilabo123")).param("patId", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("id2"));
    }

    @Test
    void getNotesByPatId_noNotes_returns200WithEmptyArray() throws Exception {
        given(noteService.getNotesByPatId(999)).willReturn(List.of());

        mockMvc.perform(get("/notes").with(httpBasic("medilabo", "medilabo123")).param("patId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getNoteById_existingId_returns200WithNoteDTO() throws Exception {
        NoteDTO dto = new NoteDTO("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(noteService.getNoteById("abc123")).willReturn(dto);

        mockMvc.perform(get("/notes/abc123").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc123"))
                .andExpect(jsonPath("$.patId").value(1));
    }

    @Test
    void getNoteById_unknownId_returns404ProblemDetail() throws Exception {
        given(noteService.getNoteById("unknown")).willThrow(new NoteNotFoundException("unknown"));

        mockMvc.perform(get("/notes/unknown").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("unknown")));
    }

    @Test
    void getNotesByPatId_missingPatId_returns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/notes").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getNotesByPatId_nonNumericPatId_returns400ProblemDetail() throws Exception {
        mockMvc.perform(get("/notes").with(httpBasic("medilabo", "medilabo123")).param("patId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addNote_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patId\":1,\"note\":\"Observation clinique.\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotesByPatId_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/notes").param("patId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNoteById_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/notes/abc123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotesByPatId_wrongPassword_returns401() throws Exception {
        mockMvc.perform(get("/notes").with(httpBasic("medilabo", "definitely-the-wrong-password"))
                        .param("patId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotesByPatId_unknownUser_returns401() throws Exception {
        mockMvc.perform(get("/notes").with(httpBasic("not-a-real-user", "medilabo123"))
                        .param("patId", "1"))
                .andExpect(status().isUnauthorized());
    }
}
