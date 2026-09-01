package com.medilabo.assessmentservice.dto;

import com.medilabo.assessmentservice.model.RiskBand;

import java.util.List;

/**
 * L'enveloppe HTTP renvoyée par {@code GET /assessments/{patId}} — enrichit le {@link RiskResult} brut avec le bloc patient dont l'UI a besoin.
 *
 * @param patId            id du patient évalué.
 * @param patient          bloc démographique imbriqué (prénom, nom, âge calculé).
 * @param riskBand         bande classifiée ; sérialisée en la chaîne exacte attendue sur le fil via le {@code @JsonValue} porté par {@link RiskBand#getDisplayName()}.
 * @param triggerCount     nombre de termes canoniques distincts détectés.
 * @param triggersDetected noms canoniques dans l'ordre chronologique du premier match.
 */
public record AssessmentResponseDTO(
        Integer patId,
        PatientBlock patient,
        RiskBand riskBand,
        int triggerCount,
        List<String> triggersDetected
) {

    /**
     * @param age années pleines calculées au moment de la requête.
     */
    public record PatientBlock(
            String firstName,
            String lastName,
            int age
    ) {
    }
}
