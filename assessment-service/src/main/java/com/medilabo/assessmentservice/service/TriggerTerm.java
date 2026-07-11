package com.medilabo.assessmentservice.service;

import java.util.List;

/**
 * Un terme déclencheur du vocabulaire fermé de risque.
 *
 * @param canonicalName le nom tel que renvoyé dans {@code triggersDetected} (accents conservés).
 * @param matchPatterns motifs en minuscules qui comptent comme match ; un seul motif pour
 *                      chaque terme sauf <em>Fumeur</em>, qui regroupe {@code fumeur}/{@code fumeuse}
 *                      en un seul déclencheur.
 */
public record TriggerTerm(String canonicalName, List<String> matchPatterns) {
}
