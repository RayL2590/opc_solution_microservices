package com.medilabo.assessmentservice.dto;

import com.medilabo.assessmentservice.model.RiskBand;

import java.util.List;

/**
 * Sortie brute de {@code RiskCalculator.compute} — le résultat algorithmique, pas l'enveloppe
 * HTTP, qui y ajoute le bloc patient et l'âge calculé.
 *
 * @param triggerCount     nombre de termes canoniques distincts détectés, dans [0, 11].
 * @param triggersDetected noms canoniques dans l'ordre du premier match (chronologique).
 * @param riskBand         la classification obtenue.
 */
public record RiskResult(
        int triggerCount,
        List<String> triggersDetected,
        RiskBand riskBand
) {
}
