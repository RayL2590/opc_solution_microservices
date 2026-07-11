package com.medilabo.assessmentservice.dto;

import java.time.LocalDate;
import java.time.Period;

/**
 * Copie locale des données démographiques d'un patient (pas de module partagé entre services
 * — contrainte d'archi). Remplie par le client upstream (Story 4.2).
 *
 * <p><b>Contrat d'entrée :</b> {@code dateOfBirth} non-null et pas dans le futur. Valider la
 * complétude du patient est la responsabilité du client upstream (Story 4.2) : un patient sans
 * date de naissance est une donnée corrompue, rejetée/mappée à la frontière du client, jamais
 * envoyée à l'algo de risque. {@link RiskCalculator} suppose donc un {@code PatientView} valide
 * et reste pur et minimal.</p>
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
     * <p>Suppose {@code dateOfBirth} non-null et {@code <= today} (contrat d'entrée du record,
     * garanti par le client upstream Story 4.2). Une {@code dateOfBirth} future donnerait un
     * âge négatif ; ce cas est filtré en amont, pas ici.</p>
     *
     * @param today la date de référence (gardée explicite pour que le calcul reste pur et déterministe).
     * @return années pleines entre {@code dateOfBirth} et {@code today}.
     */
    public int age(LocalDate today) {
        return Period.between(dateOfBirth, today).getYears();
    }
}
