package com.medilabo.assessmentservice.dto;

import java.time.Instant;

/**
 * Copie locale d'une note clinique, propre à ce service (pas de module partagé entre services).
 * Ce record ne se construit pas à partir d'une requête entrante côté assessment-service, ni via un formulaire ou une validation locale. Ses champs sont peuplés par NotesServiceClient à partir de la réponse JSON renvoyée par notes-service. C'est une copie locale et jetable des données distantes, pas un DTO alimenté par l'utilisateur.
 *
 * @param id        le {@code _id} MongoDB de la note.
 * @param patId     id du patient propriétaire.
 * @param patient   nom de famille dénormalisé.
 * @param note      texte libre, scanné pour y détecter les termes déclencheurs.
 * @param createdAt horodatage de création (UTC), sert à ordonner les détections dans le temps.
 */
public record NoteView(
        String id,
        Integer patId,
        String patient,
        String note,
        Instant createdAt
) {
}
