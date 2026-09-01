package com.medilabo.assessmentservice.dto;

import java.time.LocalDate;
import java.time.Period;

/**
 * Copie locale des données démographiques d'un patient (pas de module partagé entre services, contrainte d'archi). Ce record ne se construit pas à partir d'une requête entrante côté
 assessment-service, ni via un formulaire ou une validation locale. Ses champs sont peuplés par PatientServiceClient à partir de la réponse JSON renvoyée par patient-service. C'est une copie locale et jetable des données distantes, pas un DTO alimenté par l'utilisateur.
 * <p><b>Contrat d'entrée :</b> {@code dateOfBirth} non-null et pas dans le futur. Cette validation, c'est le boulot du client upstream : un patient sans date de naissance, c'est une donnée corrompue, on la rejette à la frontière, elle n'arrive jamais jusqu'à l'algo de risque.
 * Le calculateur peut donc supposer un {@code PatientView} valide et rester pur.</p>
 *
 * @param id           id entier du patient.
 * @param firstName    prénom.
 * @param lastName     nom de famille.
 * @param dateOfBirth  date de naissance (sans timezone) ; <b>non-null</b>, pas dans le futur.
 * @param gender       code sur un caractère, {@code M} ou {@code F}.
 */
public record PatientView(
        Integer id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender
) {

    /**
     * Calcule l'âge du patient en années pleines à la date donnée.
     *
     * <p>Suppose {@code dateOfBirth} non-null et {@code <= referenceDate} (contrat d'entrée du
     * record). Une date future donnerait un âge négatif, mais ce cas est filtré en amont, pas ici.</p>
     *
     * @param referenceDate date à laquelle l'âge est calculé (injectée plutôt que lue de l'horloge courante, pour garder un calcul pur et déterministe).
     * @return années pleines entre {@code dateOfBirth} et {@code referenceDate}.
     */
    public int age(LocalDate referenceDate) {
        return Period.between(dateOfBirth, referenceDate).getYears();
    }
}
