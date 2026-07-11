package com.medilabo.assessmentservice.dto;

import java.time.Instant;

/**
 * Copie locale d'une note clinique, propre à ce service (pas de module partagé entre services).
 * Remplie par le client upstream (Story 4.2).
 *
 * @param id        le {@code _id} MongoDB de la note.
 * @param patId     id du patient propriétaire.
 * @param patient   nom de famille dénormalisé.
 * @param note      texte libre scanné pour les termes déclencheurs.
 * @param createdAt horodatage de création (UTC) ; détermine l'ordre chronologique de détection.
 */
public record NoteView(
        String id,
        Integer patId,
        String patient,
        String note,
        Instant createdAt
) {
}
