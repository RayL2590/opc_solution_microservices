package com.medilabo.assessmentservice.service;

import java.util.List;

/**
 * Un terme déclencheur du vocabulaire fermé de risque.
 *
 * @param canonicalName le nom tel qu'il apparaît dans {@code triggersDetected} (avec les accents).
 * @param matchPatterns motifs en minuscules qui comptent comme match ; en général un seul motif
 *                      par terme, sauf <em>Fumeur</em> qui regroupe {@code fumeur}/{@code fumeuse}
 *                      sous un seul déclencheur.
 */
public record TriggerTerm(String canonicalName, List<String> matchPatterns) {
}
