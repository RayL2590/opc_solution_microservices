package com.medilabo.assessmentservice.controller;

import com.medilabo.assessmentservice.config.SecurityConfig;
import com.medilabo.assessmentservice.dto.AssessmentResponseDTO;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.GlobalExceptionHandler;
import com.medilabo.assessmentservice.exception.IncompletePatientDataException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import com.medilabo.assessmentservice.model.RiskBand;
import com.medilabo.assessmentservice.service.AssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice {@code @WebMvcTest} pour {@link AssessmentController} : {@link AssessmentService} mocké,
 * vrai {@link SecurityConfig} importé et exercé via {@code httpBasic(...)}. On vérifie la forme
 * de l'enveloppe FR-8 en 200, les codes 404/502/504/422 via {@link GlobalExceptionHandler}, et
 * les rejets en 401 (pas de credentials, mauvais mot de passe, utilisateur inconnu).
 */
@WebMvcTest(AssessmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AssessmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    @Test
    void getAssessment_happyPath_returnsFr8Envelope() throws Exception {
        AssessmentResponseDTO dto = new AssessmentResponseDTO(
                4,
                new AssessmentResponseDTO.PatientBlock("TestEarlyOnset", "Patient4", 23),
                RiskBand.EARLY_ONSET,
                7,
                List.of("Anticorps", "Réaction", "Hémoglobine A1C", "Taille", "Poids", "Cholestérol", "Vertiges"));
        when(assessmentService.assess(4)).thenReturn(dto);

        mockMvc.perform(get("/assessments/4").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patId").value(4))
                .andExpect(jsonPath("$.patient.firstName").value("TestEarlyOnset"))
                .andExpect(jsonPath("$.patient.lastName").value("Patient4"))
                .andExpect(jsonPath("$.patient.age").value(23))
                .andExpect(jsonPath("$.riskBand").value("Early Onset"))
                .andExpect(jsonPath("$.triggerCount").value(7))
                .andExpect(jsonPath("$.triggersDetected", org.hamcrest.Matchers.hasSize(7)))
                .andExpect(jsonPath("$.triggersDetected[0]").value("Anticorps"));
    }

    @Test
    void getAssessment_patientNotFound_returns404() throws Exception {
        when(assessmentService.assess(1)).thenThrow(
                new UpstreamNotFoundException("Patient introuvable : id=1"));

        mockMvc.perform(get("/assessments/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAssessment_badGateway_returns502() throws Exception {
        when(assessmentService.assess(1)).thenThrow(
                new BadGatewayException("Erreur upstream patient-service pour id=1"));

        mockMvc.perform(get("/assessments/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isBadGateway());
    }

    @Test
    void getAssessment_gatewayTimeout_returns504() throws Exception {
        when(assessmentService.assess(1)).thenThrow(
                new GatewayTimeoutException("patient-service inaccessible pour id=1",
                        new RuntimeException("connection refused")));

        mockMvc.perform(get("/assessments/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().isGatewayTimeout());
    }

    @Test
    void getAssessment_incompletePatientData_returns422() throws Exception {
        when(assessmentService.assess(1)).thenThrow(new IncompletePatientDataException(1));

        mockMvc.perform(get("/assessments/1").with(httpBasic("medilabo", "medilabo123")))
                .andExpect(status().is(422));
    }

    @Test
    void getAssessment_withoutCredentials_returns401() throws Exception {
        mockMvc.perform(get("/assessments/4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAssessment_wrongPassword_returns401() throws Exception {
        mockMvc.perform(get("/assessments/4").with(httpBasic("medilabo", "definitely-the-wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAssessment_unknownUser_returns401() throws Exception {
        mockMvc.perform(get("/assessments/4").with(httpBasic("not-a-real-user", "medilabo123")))
                .andExpect(status().isUnauthorized());
    }
}
