package com.medilabo.assessmentservice.dto;

import java.util.List;

/**
 * L'enveloppe HTTP FR-8 renvoyée par {@code GET /assessments/{patId}} — enrichit le
 * {@link RiskResult} brut avec le bloc patient dont l'UI a besoin.
 *
 * @param patId            id du patient évalué.
 * @param patient          bloc démographique imbriqué (prénom, nom, âge calculé).
 * @param riskBand         chaîne exacte FR-8, via {@code RiskBand.getDisplayName()}.
 * @param triggerCount     nombre de termes canoniques distincts détectés.
 * @param triggersDetected noms canoniques dans l'ordre chronologique du premier match.
 */
public record AssessmentResponseDTO(
        Integer patId,
        PatientBlock patient,
        String riskBand,
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
