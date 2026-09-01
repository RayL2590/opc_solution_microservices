package com.medilabo.frontservice.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientResponseException;

import com.medilabo.frontservice.config.SecurityConfig;
import com.medilabo.frontservice.dto.AssessmentView;
import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;
import com.medilabo.frontservice.dto.PhoneCountry;
import com.medilabo.frontservice.service.PatientUiService;

/**
 * Tranche @WebMvcTest pour PatientUiController — SecurityConfig réelle (HTTP Basic exercé), PatientUiService mocké. CSRF désactivé dans SecurityConfig, les POST n'ont pas besoin de csrf().
 */
@WebMvcTest(PatientUiController.class)
@Import(SecurityConfig.class)
class PatientUiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientUiService patientUiService;

    @Test
    void listPatients_authenticated_returns200WithPatientsList() throws Exception {
        PatientView p1 = new PatientView(1L, "Jean", "Dupont",
                LocalDate.of(1980, 1, 15), "M", null, null);
        PatientView p2 = new PatientView(2L, "Marie", "Martin",
                LocalDate.of(1975, 6, 30), "F", null, null);
        given(patientUiService.getAllPatients()).willReturn(List.of(p1, p2));

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
        given(patientUiService.createPatient(any(PatientForm.class))).willReturn(created);

        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "F"))
                .andExpect(status().is3xxRedirection())
                // après création, retour à la liste, pas vers la fiche détail
                .andExpect(redirectedUrl("/ui/patients"));
    }

    @Test
    void createPatient_serviceRejectsUnusableGatewayResponse_propagatesIllegalState() {
        // @WebMvcTest sans handler global : MockMvc rethrow l'IllegalStateException directement (Spring 6+ retire NestedServletException).
        given(patientUiService.createPatient(any(PatientForm.class)))
                .willThrow(new IllegalStateException(
                        "Gateway returned a null patient or null id after creation — cannot redirect"));

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
                        .param("gender", "X"))        // hors {M,F}
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "gender"));
    }

    @Test
    void createPatient_genderU_returns400WithErrors() throws Exception {
        // U (inconnu) a été retiré du domaine : verrouille le rejet côté formulaire, sinon un retour à ^[MFU]$ repasserait au vert sans que rien ne le signale.
        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Alice")
                        .param("lastName", "Martin")
                        .param("dateOfBirth", "1990-03-20")
                        .param("gender", "U"))
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
        given(patientUiService.getPatient(1L)).willReturn(existing);

        mockMvc.perform(get("/ui/patients/1/edit").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeExists("patientForm"))
                .andExpect(model().attribute("patientId", 1L));
    }

    @Test
    void showEditForm_legacyNationalPhone_preselectsUsDialingCode() throws Exception {
        // Numéro hérité au format national du sujet OpenClassrooms, sans indicatif : il doit être reconnu comme US, sinon le formulaire propose FR et le numéro devient invalide.
        PatientView existing = new PatientView(1L, "Test", "TestBorderline",
                LocalDate.of(1945, 6, 24), "M", "2 High St", "200-333-4444");
        given(patientUiService.getPatient(1L)).willReturn(existing);

        mockMvc.perform(get("/ui/patients/1/edit").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("patientForm",
                        hasProperty("phoneCountry", equalTo(PhoneCountry.US))));
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
        given(patientUiService.updatePatient(eq(1L), any(PatientForm.class))).willReturn(updated);

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
    void updatePatient_addressOnlyChange_keepsUntouchedLegacyPhoneValid() throws Exception {
        // Régression : modifier la seule adresse d'un patient du jeu de données par défaut échouait sur une erreur de validation du téléphone, pourtant jamais saisi, le formulaire renvoyait le numéro US hérité avec un indicatif FR deviné par défaut.
        PatientView updated = new PatientView(1L, "Test", "TestBorderline",
                LocalDate.of(1945, 6, 24), "M", "2 High Street", "+12003334444");
        given(patientUiService.updatePatient(eq(1L), any(PatientForm.class))).willReturn(updated);

        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestBorderline")
                        .param("dateOfBirth", "1945-06-24")
                        .param("gender", "M")
                        .param("address", "2 High Street")   // seul champ réellement modifié
                        .param("phoneCountry", "US")
                        .param("phone", "200-333-4444"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/ui/patients"));
    }

    @Test
    void updatePatient_upstreamRejectsField_reRendersFormWithFieldError() throws Exception {
        // Regression du 500 Whitelabel : quand patient-service refuse un champ que le front avait laisse passer (indicatif telephonique non synchronise, par exemple), l'utilisateur doit retrouver son formulaire et le message, pas une page d'erreur.
        given(patientUiService.updatePatient(eq(1L), any(PatientForm.class)))
                .willThrow(upstreamBadRequest(
                        "{\"detail\":\"La validation du patient a échoué\",\"status\":400,"
                                + "\"errors\":{\"phone\":\"Le téléphone doit être au format international\"}}"));

        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestBorderline")
                        .param("dateOfBirth", "1945-06-24")
                        .param("gender", "M")
                        .param("phoneCountry", "US")
                        .param("phone", "+12003334444"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "phone"));
    }

    @Test
    void updatePatient_upstreamRejectsWithoutFieldErrors_reRendersFormWithGlobalError() throws Exception {
        // Refus amont sans map "errors" exploitable : le detail du ProblemDetail devient une erreur globale, affichee en tete de formulaire.
        given(patientUiService.updatePatient(eq(1L), any(PatientForm.class)))
                .willThrow(upstreamBadRequest("{\"detail\":\"La validation du patient a échoué\",\"status\":400}"));

        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestBorderline")
                        .param("dateOfBirth", "1945-06-24")
                        .param("gender", "M"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeHasErrors("patientForm"));
    }

    @Test
    void createPatient_upstreamRejectsField_reRendersFormWithFieldError() throws Exception {
        given(patientUiService.createPatient(any(PatientForm.class)))
                .willThrow(upstreamBadRequest(
                        "{\"status\":400,\"errors\":{\"phone\":\"Le téléphone doit être au format international\"}}"));

        mockMvc.perform(post("/ui/patients")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "Nouveau")
                        .param("dateOfBirth", "1990-01-01")
                        .param("gender", "F")
                        .param("phoneCountry", "US")
                        .param("phone", "+12003334444"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/new"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "phone"));
    }

    @Test
    void updatePatient_upstreamServerError_isNotSwallowed() throws Exception {
        // Une panne amont (5xx) n'est pas une faute de saisie : elle doit rester visible, surtout pas etre repeinte en erreur de formulaire.
        given(patientUiService.updatePatient(eq(1L), any(PatientForm.class)))
                .willThrow(new RestClientResponseException(
                        "500 Internal Server Error", 500, "Internal Server Error",
                        new HttpHeaders(), null, StandardCharsets.UTF_8));

        assertThrows(Exception.class, () ->
                mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestBorderline")
                        .param("dateOfBirth", "1945-06-24")
                        .param("gender", "M")));
    }

    /** Reproduit un 400 de patient-service tel que le RestClient le remonte au front. */
    private static RestClientResponseException upstreamBadRequest(String body) {
        return new RestClientResponseException(
                "400 Bad Request", 400, "Bad Request", new HttpHeaders(),
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
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
    void updatePatient_genderU_returns400WithErrors() throws Exception {
        // Le formulaire d'édition applique le même @Pattern que la création : rééditer un patient historiquement stocké en U impose de choisir M ou F, jamais de le laisser tel quel.
        mockMvc.perform(post("/ui/patients/1/edit")
                        .with(httpBasic("medilabo", "medilabo123"))
                        .param("firstName", "Test")
                        .param("lastName", "TestNone")
                        .param("dateOfBirth", "1966-12-31")
                        .param("gender", "U"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("patients/edit"))
                .andExpect(model().attributeHasFieldErrors("patientForm", "gender"));
    }

    @Test
    void showPatientDetail_authenticated_returns200WithPatientAndNotes() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", "1 Brookside St", "100-222-3333");
        NoteView note = new NoteView("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        given(patientUiService.loadPatientDetail(1L)).willReturn(
                new PatientUiService.PatientDetail(patient, List.of(note),
                        new AssessmentView("None", 0, List.of())));

        mockMvc.perform(get("/ui/patients/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(view().name("patients/detail"))
                .andExpect(model().attributeExists("patient"))
                .andExpect(model().attribute("notes", hasSize(1)))
                .andExpect(model().attributeExists("noteForm"))
                .andExpect(model().attributeExists("assessment"));
    }

    @Test
    void showPatientDetail_noNotes_returns200WithEmptyNotesList() throws Exception {
        PatientView patient = new PatientView(1L, "Test", "TestNone",
                LocalDate.of(1966, 12, 31), "F", null, null);
        given(patientUiService.loadPatientDetail(1L)).willReturn(
                new PatientUiService.PatientDetail(patient, List.of(),
                        new AssessmentView("None", 0, List.of())));

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
        given(patientUiService.addNote(eq(1L), any(NoteForm.class))).willReturn(
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
        given(patientUiService.loadPatientDetail(1L)).willReturn(
                new PatientUiService.PatientDetail(patient, List.of(),
                        new AssessmentView("None", 0, List.of())));

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
