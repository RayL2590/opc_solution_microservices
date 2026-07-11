package com.medilabo.frontservice.dto;

import java.util.List;

/**
 * DTO côté front (pas de module partagé — duplication intentionnelle, frontière polyglotte).
 * Miroir de AssessmentResponseDTO (assessment-service) : seuls les champs affichés en détail patient.
 */
public record AssessmentView(
        String riskBand,
        int triggerCount,
        List<String> triggersDetected
) {}
