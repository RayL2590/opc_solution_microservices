package com.medilabo.assessmentservice.service;

import java.util.List;

/**
 * Le set fermé et versionné des onze termes déclencheurs canoniques (PRD §3, FR-9).
 *
 * <p>L'ordre suit l'énumération du PRD §3 et compte : {@code triggersDetected} tranche les
 * ex-aequo d'une même note dans cet ordre de déclaration. Les motifs sont mis en minuscules
 * une fois ici (accents conservés) pour que {@link RiskCalculator} n'ait qu'à lowercase le
 * texte de la note au moment du match. Changer le vocabulaire, c'est du code, jamais de la config.</p>
 */
public final class TriggerVocabulary {

    /** Les onze termes déclencheurs canoniques, dans l'ordre fixe du PRD §3. */
    public static final List<TriggerTerm> TERMS = List.of(
            new TriggerTerm("Hémoglobine A1C", List.of("hémoglobine a1c")),
            new TriggerTerm("Microalbumine", List.of("microalbumine")),
            new TriggerTerm("Taille", List.of("taille")),
            new TriggerTerm("Poids", List.of("poids")),
            new TriggerTerm("Fumeur", List.of("fumeur", "fumeuse")),
            new TriggerTerm("Anormal", List.of("anormal")),
            new TriggerTerm("Cholestérol", List.of("cholestérol")),
            new TriggerTerm("Vertiges", List.of("vertige")),
            new TriggerTerm("Rechute", List.of("rechute")),
            new TriggerTerm("Réaction", List.of("réaction")),
            new TriggerTerm("Anticorps", List.of("anticorps"))
    );

    private TriggerVocabulary() {
        // pas instanciable, juste un porte-constantes
    }
}
