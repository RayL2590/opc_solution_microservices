package com.medilabo.patientservice.controller;

import com.medilabo.patientservice.config.SecurityConfig;
import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.exception.GlobalExceptionHandler;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tranche @WebMvcTest pour PatientController — SecurityConfig réelle (chaîne HTTP Basic exercée),
 * GlobalExceptionHandler importé (404 ProblemDetail), service mocké (DB-free).
 */
@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private PatientDTO samplePatient() {
        return PatientDTO.builder()
                .id(1L)
                .firstName("Test")
                .lastName("TestNone")
                .dateOfBirth(LocalDate.of(1966, 12, 31))
                .gender("F")
                .build();
    }

    @Test
    void listPatients_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPatients_withCredentials_returns200AndJsonArray() throws Exception {
        given(patientService.getAllPatients()).willReturn(List.of(samplePatient()));

        mockMvc.perform(get("/patients").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].lastName").value("TestNone"));
    }

    @Test
    void getPatientById_existingId_returns200AndDto() throws Exception {
        given(patientService.getPatientById(1L)).willReturn(samplePatient());

        mockMvc.perform(get("/patients/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastName").value("TestNone"));
    }

    @Test
    void getPatientById_missingId_returns404ProblemDetail() throws Exception {
        given(patientService.getPatientById(999L))
                .willThrow(new PatientNotFoundException(999L));

        mockMvc.perform(get("/patients/999").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    void getPatientById_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/patients/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPatients_validUserWrongPassword_returns401() throws Exception {
        // Exercises the password-MATCH path (not just the no-creds gate): a known user with a
        // wrong password must be rejected. This is the only slice test that proves the seeded
        // BCrypt hash — resolved from medilabo.password-bcrypt in application.properties — is
        // actually matched. A NoOp encoder or a broken hash bridge would make this pass with the
        // wrong password and go red here.
        mockMvc.perform(get("/patients").with(httpBasic("medilabo", "definitely-the-wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPatients_unknownUser_returns401() throws Exception {
        mockMvc.perform(get("/patients").with(httpBasic("not-a-real-user", "medilabo123")))
                .andExpect(status().isUnauthorized());
    }

    private static final String VALID_BODY = """
            {"firstName":"Jean","lastName":"Dupont","dateOfBirth":"1990-01-01","gender":"M"}
            """;

    @Test
    void createPatient_validBody_returns201AndPersistedDto() throws Exception {
        given(patientService.createPatient(any(PatientDTO.class))).willReturn(samplePatient());

        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastName").value("TestNone"));
    }

    @Test
    void createPatient_blankRequiredField_returns400WithErrorsMapKeys() throws Exception {
        // firstName blank + lastName missing → two field-keyed errors; dateOfBirth/gender present.
        String body = """
                {"firstName":"","dateOfBirth":"1990-01-01","gender":"M"}
                """;

        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists());
    }

    @Test
    void createPatient_allRequiredFieldsInvalid_returns400WithAllKeys() throws Exception {
        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.lastName").exists())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists())
                .andExpect(jsonPath("$.errors.gender").exists());
    }

    @Test
    void createPatient_invalidGender_returns400WithGenderKey() throws Exception {
        // Genre hors {M,F,U} → rejeté par @Pattern.
        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.gender").exists());
    }

    @Test
    void createPatient_futureBirthDate_returns400WithDateKey() throws Exception {
        // Date dans le futur → rejetée par @BirthDate.
        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"dateOfBirth\":\"2999-01-01\",\"gender\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    void createPatient_olderThan160Years_returns400WithDateKey() throws Exception {
        // Âge > 160 ans → rejeté par @BirthDate.
        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"dateOfBirth\":\"1800-01-01\",\"gender\":\"M\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateOfBirth").exists());
    }

    @Test
    void createPatient_genderU_isAccepted() throws Exception {
        // U (inconnu) est une valeur valide.
        given(patientService.createPatient(any(PatientDTO.class))).willReturn(samplePatient());
        mockMvc.perform(post("/patients").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"U\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createPatient_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(post("/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePatient_existingId_validBody_returns200AndUpdatedDto() throws Exception {
        given(patientService.updatePatient(eq(1L), any(PatientDTO.class))).willReturn(samplePatient());

        mockMvc.perform(put("/patients/1").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.lastName").value("TestNone"));
    }

    @Test
    void updatePatient_blankRequiredField_returns400WithErrorsMapKey() throws Exception {
        String body = """
                {"firstName":"Jean","lastName":"Dupont","dateOfBirth":"1990-01-01","gender":""}
                """;

        mockMvc.perform(put("/patients/1").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.gender").exists());
    }

    @Test
    void updatePatient_missingId_returns404ProblemDetail() throws Exception {
        given(patientService.updatePatient(eq(999L), any(PatientDTO.class)))
                .willThrow(new PatientNotFoundException(999L));

        mockMvc.perform(put("/patients/999").with(httpBasic("medilabo", "medilabo123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getPatientById_malformedId_returns400ProblemDetail() throws Exception {
        // Non-numeric {id} fails @PathVariable Long conversion BEFORE the controller —
        // MethodArgumentTypeMismatchException must stay inside the RFC 7807 envelope.
        mockMvc.perform(get("/patients/abc").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
