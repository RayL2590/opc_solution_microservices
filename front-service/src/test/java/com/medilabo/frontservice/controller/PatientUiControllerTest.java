package com.medilabo.frontservice.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medilabo.frontservice.client.NotesGatewayClient;
import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.config.SecurityConfig;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Tranche @WebMvcTest pour PatientUiController — SecurityConfig réelle (HTTP Basic exercé),
 * PatientGatewayClient et NotesGatewayClient mockés. CSRF désactivé dans SecurityConfig, les POST n'ont pas besoin de csrf().
 */
@WebMvcTest(PatientUiController.class)
@Import(SecurityConfig.class)
class PatientUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientGatewayClient patientGatewayClient;

    @MockitoBean
    private NotesGatewayClient notesGatewayClient;

    @Test
    void listPatients_authenticated_returns200WithPatientsList() throws Exception {
        PatientView p1 = new PatientView(1L, "Jean", "Dupont",
                LocalDate.of(1980, 1, 15), "M", null, null);
        PatientView p2 = new PatientView(2L, "Marie", "Martin",
                LocalDate.of(1975, 6, 30), "F", null, null);
        given(patientGatewayClient.getAllPatients()).willReturn(List.of(p1, p2));

        mockMvc.perform(get("/ui/patients").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/list"))
                .andExpect(model().attributeExists("patients"))
                .andExpect(model().attribute("patients", hasSize(2)));
    }

    @Test
    void listPatients_unauthenticated_returns401BasicChallenge() throws Exception {
        mockMvc.perform(get("/ui/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void showNewPatientForm_authenticated_returns200WithEmptyForm() throws Exception {
        mockMvc.perform(get("/ui/patients/new").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeExists("patientForm"));
    }

    @Test
    void showNewPatientForm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/ui/patients/new"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPatient_validForm_redirectsToList() throws Exception {
        PatientView created = new PatientView(42L, "Alice", "Martin",
                LocalDate.of(1990, 3, 20), "F", null, null);
        given(patientGatewayClient.createPatient(any(PatientForm.class))).willReturn(created);

        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "F"))
                .andExpect(status().is3xxRedirection())
                // Retour à la liste — la fiche détail est hors Sprint 1 (story 5.3).
                .andExpect(redirectedUrl("/ui/patients"));
    }

    @Test
    void createPatient_gatewayReturnsNull_throwsIllegalState() {
        // @WebMvcTest sans handler global : MockMvc rethrow l'IllegalStateException directement (Spring 6+ retire NestedServletException).
        given(patientGatewayClient.createPatient(any(PatientForm.class))).willReturn(null);

        assertThrows(Exception.class, () ->
            mockMvc.perform(post("/ui/patients")
                            .with(httpBasic("medilabo", "medilabo123"))
                            .param("firstName", "Alice")
                            .param("lastName", "Martin")
                            .param("dateOfBirth", "1990-03-20")
                            .param("gender", "F")));
    }

    @Test
    void createPatient_invalidForm_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "")        // blank — @NotBlank
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "F"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "firstName"));
    }

    @Test
    void createPatient_invalidGender_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "X"))        // hors {M,F,U}
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "gender"));
    }

    @Test
    void createPatient_futureBirthDate_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "2999-01-01")   // futur
                        .param("gender", "F"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "dateOfBirth"));
    }

    @Test
    void showEditForm_authenticated_returns200WithPrefilledForm() throws Exception {
        PatientView existing = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", "1 Brookside St", "100-222-3333");
        given(patientGatewayClient.getPatient(1L)).willReturn(existing);

        mockMvc.perform(get("/ui/patients/1/edit").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeExists("patientForm"))
                .andExpect(model().attribute("patientId", 1L));
    }

    @Test
    void showEditForm_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/ui/patients/1/edit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePatient_validForm_redirectsToList() throws Exception {
        PatientView updated = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", "New Address", null);
        given(patientGatewayClient.updatePatient(eq(1L), any(PatientForm.class))).willReturn(updated);

        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestNone")
                        .param("dateOfBirth", "1966-12-31")
                        .param("gender", "F"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/patients"));
    }

    @Test
    void updatePatient_invalidForm_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "")        // blank — @NotBlank
                        .param("lastName", "TestNone")
                        .param("dateOfBirth", "1966-12-31")
                        .param("gender", "F"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "firstName"));
    }

    @Test
    void showPatientDetail_authenticated_returns200WithPatientAndNotes() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", "1 Brookside St", "100-222-3333");
        NoteView note = new NoteView("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(patientGatewayClient.getPatient(1L)).willReturn(patient);
        given(notesGatewayClient.getNotesByPatId(1L)).willReturn(List.of(note));

        mockMvc.perform(get("/ui/patients/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attributeExists("patient"))
                .andExpect(model().attribute("notes", hasSize(1)))
                .andExpect(model().attributeExists("noteForm"));
    }

    @Test
    void showPatientDetail_noNotes_returns200WithEmptyNotesList() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", null, null);
        given(patientGatewayClient.getPatient(1L)).willReturn(patient);
        given(notesGatewayClient.getNotesByPatId(1L)).willReturn(List.of());

        mockMvc.perform(get("/ui/patients/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attribute("notes", hasSize(0)));
    }

    @Test
    void showPatientDetail_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/ui/patients/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addNote_validForm_redirectsToDetailPage() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", null, null);
        given(patientGatewayClient.getPatient(1L)).willReturn(patient);
        given(notesGatewayClient.addNote(any())).willReturn(
                new NoteView("abc123", 1, "TestNone", "Observation clinique.", Instant.now()));

        mockMvc.perform(post("/ui/patients/1/notes")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("note", "Observation clinique."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/patients/1"));
    }

    @Test
    void addNote_blankNote_returns400WithFieldError() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", null, null);
        given(patientGatewayClient.getPatient(1L)).willReturn(patient);
        given(notesGatewayClient.getNotesByPatId(1L)).willReturn(List.of());

        mockMvc.perform(post("/ui/patients/1/notes")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("note", ""))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attributeHasFieldErrors("noteForm", "note"));
    }

    @Test
    void addNote_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/ui/patients/1/notes")
                        .param("note", "Observation clinique."))
                .andExpect(status().isUnauthorized());
    }
}
