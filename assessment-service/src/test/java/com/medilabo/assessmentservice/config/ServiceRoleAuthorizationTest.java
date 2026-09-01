package com.medilabo.assessmentservice.config;

import com.medilabo.assessmentservice.controller.AssessmentController;
import com.medilabo.assessmentservice.dto.AssessmentResponseDTO;
import com.medilabo.assessmentservice.exception.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Séparation des privilèges sur l'API d'évaluation.
 *
 * <p>Seul front-service consomme les évaluations, et en lecture seule. svc-assessment, c'est l'identité SORTANTE de ce service : accepter ses propres credentials en entrée n'aurait aucun usage légitime, ça ne ferait qu'élargir la surface d'attaque pour rien.
 */
@WebMvcTest(AssessmentController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ServiceRoleAuthorizationTest {

    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String SVC_FRONT_USER = "svc-front";
    private static final String SVC_FRONT_RAW_PASSWORD = "svcfront123";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";
    private static final String SVC_ASSESSMENT_RAW_PASSWORD = "svcassess123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssessmentService assessmentService;

    private AssessmentResponseDTO sampleAssessment() {
        return new AssessmentResponseDTO(
                4,
                new AssessmentResponseDTO.PatientBlock("TestEarlyOnset", "Patient4", 23),
                RiskBand.EARLY_ONSET,
                7,
                List.of("Anticorps"));
    }

    @Test
    void frontServiceAccountKeepsTheReadAccessItNeeds() throws Exception {
        when(assessmentService.assess(4)).thenReturn(sampleAssessment());

        mockMvc.perform(get("/assessments/4")
                        .with(httpBasic(SVC_FRONT_USER, SVC_FRONT_RAW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void clinicianKeepsTheReadAccess() throws Exception {
        when(assessmentService.assess(4)).thenReturn(sampleAssessment());

        mockMvc.perform(get("/assessments/4")
                        .with(httpBasic(DEMO_USER, DEMO_RAW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void outboundServiceAccountIsNotAcceptedOnTheInboundApi() throws Exception {
        when(assessmentService.assess(4)).thenReturn(sampleAssessment());

        mockMvc.perform(get("/assessments/4")
                        .with(httpBasic(SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
