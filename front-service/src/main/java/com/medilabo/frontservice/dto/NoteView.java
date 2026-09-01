package com.medilabo.frontservice.dto;

import java.time.Instant;

/**
 * DTO côté front : chaque service garde sa propre copie des concepts qu'il consomme, aucun module partagé entre services.
 * Miroir de NoteDTO (notes-service).
 */
public record NoteView(
        String id,
        Integer patId,
        String patient,
        String note,
        Instant createdAt
) {}
