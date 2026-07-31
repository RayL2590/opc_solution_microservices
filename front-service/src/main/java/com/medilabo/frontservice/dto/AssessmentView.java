package com.medilabo.frontservice.dto;

import java.util.List;

/**
 * DTO côté front : chaque service garde sa propre copie des concepts qu'il consomme, aucun
 * module partagé entre services (décision D-DATA-3, architecture.md).
 * Miroir de AssessmentResponseDTO (assessment-service) : seuls les champs affichés en détail patient.
 */
public record AssessmentView(
        String riskBand,
        int triggerCount,
        List<String> triggersDetected
) {}
