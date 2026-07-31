package com.medilabo.notesservice.config;

import com.medilabo.notesservice.controller.NoteController;
import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.exception.GlobalExceptionHandler;
import com.medilabo.notesservice.service.NoteService;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie la séparation des privilèges entre le clinicien (ROLE_USER) et les comptes machine.
 *
 * <p>assessment-service lit les notes d'un patient pour calculer un risque, rien de plus,
 * donc pas de raison qu'il puisse en créer. Ça, c'est le clinicien, via svc-front.
 */
@WebMvcTest(value = NoteController.class,
        excludeAutoConfiguration = {
                MongoAutoConfiguration.class,
                DataMongoAutoConfiguration.class,
                DataMongoRepositoriesAutoConfiguration.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ServiceRoleAuthorizationTest {

    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";
    private static final String SVC_ASSESSMENT_RAW_PASSWORD = "svcassess123";

    private static final String NOTE_JSON = """
            {"patId":1,"patient":"TestNone","note":"Le patient déclare une douleur"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    private NoteDTO sampleNote() {
        return new NoteDTO("abc123", 1, "TestNone", "Le patient déclare une douleur", Instant.now());
    }

    @Test
    void assessmentServiceAccountCannotCreateANote() throws Exception {
        given(noteService.addNote(any())).willReturn(sampleNote());

        mockMvc.perform(post("/notes")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void assessmentServiceAccountKeepsTheReadAccessItNeeds() throws Exception {
        given(noteService.getNotesByPatId(1)).willReturn(List.of(sampleNote()));

        mockMvc.perform(get("/notes?patId=1")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void clinicianKeepsFullReadWriteAccess() throws Exception {
        given(noteService.addNote(any())).willReturn(sampleNote());
        given(noteService.getNotesByPatId(1)).willReturn(List.of(sampleNote()));

        mockMvc.perform(get("/notes?patId=1")
                        .with(httpBasic(DEMO_USER, DEMO_RAW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/notes")
                        .with(httpBasic(DEMO_USER, DEMO_RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOTE_JSON))
                .andExpect(status().isCreated());
    }
}
