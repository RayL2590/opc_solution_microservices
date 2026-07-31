package com.medilabo.frontservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medilabo.frontservice.dto.AssessmentView;

/**
 * Client Gateway pour l'évaluation du risque. Authentification sortante gérée par ServiceAccountAuthInitializer.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssessmentGatewayClient {

    private final RestClient gatewayClient;

    public AssessmentView getAssessment(Long patId) {
        AssessmentView assessment = gatewayClient.get()
                .uri("/assessments/{patId}", patId)
                .retrieve()
                .body(AssessmentView.class);
        log.debug("Fetched assessment for patId={}, riskBand={}", patId, assessment != null ? assessment.riskBand() : null);
        return assessment;
    }
}
