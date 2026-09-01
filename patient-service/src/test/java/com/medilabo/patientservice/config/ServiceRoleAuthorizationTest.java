package com.medilabo.patientservice.config;

import com.medilabo.patientservice.controller.PatientController;
import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.exception.GlobalExceptionHandler;
import com.medilabo.patientservice.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Séparation des privilèges entre le clinicien (ROLE_USER) et les comptes machine (ROLE_SERVICE).
 *
 * patient-service n'a qu'un seul appelant machine légitime en lecture : assessment-service,
 * qui fait juste du {@code GET /patients/{id}}. Aucun compte de service n'a besoin d'écrire ici : les créations et modifications viennent du clinicien via front-service, qui relaie son propre compte de service — c'est pour ça que svc-front garde le droit d'écrire.
 */
@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ServiceRoleAuthorizationTest {

    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";
    private static final String SVC_ASSESSMENT_RAW_PASSWORD = "svcassess123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private static final String PATIENT_JSON = """
            {"firstName":"Test","lastName":"TestNone","dateOfBirth":"1966-12-31","gender":"F"}""";

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
    void assessmentServiceAccountCannotCreateAPatient() throws Exception {
        given(patientService.createPatient(any())).willReturn(samplePatient());

        mockMvc.perform(post("/patients")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void assessmentServiceAccountCannotUpdateAPatient() throws Exception {
        given(patientService.updatePatient(any(), any())).willReturn(samplePatient());

        mockMvc.perform(put("/patients/1")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void assessmentServiceAccountKeepsTheReadAccessItNeeds() throws Exception {
        given(patientService.getPatientById(1L)).willReturn(samplePatient());

        mockMvc.perform(get("/patients/1")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void clinicianKeepsFullReadWriteAccess() throws Exception {
        given(patientService.createPatient(any())).willReturn(samplePatient());
        given(patientService.getPatientById(1L)).willReturn(samplePatient());

        mockMvc.perform(get("/patients/1")
                        .with(httpBasic(DEMO_USER, DEMO_RAW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/patients")
                        .with(httpBasic(DEMO_USER, DEMO_RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PATIENT_JSON))
                .andExpect(status().isCreated());
    }
}
