package com.medilabo.frontservice.dto;

import java.time.Instant;

/**
 * DTO côté front (pas de module partagé — duplication intentionnelle, frontière polyglotte).
 * Miroir de NoteDTO (notes-service).
 */
public record NoteView(
        String id,
        Integer patId,
        String patient,
        String note,
        Instant createdAt
) {}
