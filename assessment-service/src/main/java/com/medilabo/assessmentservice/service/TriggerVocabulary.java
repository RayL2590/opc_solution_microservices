package com.medilabo.assessmentservice.service;

import java.util.List;

/**
 * Le set fermé des onze termes déclencheurs canoniques (PRD §3, FR-9, voir
 * {@code Documentation/requirements-glossary.md}).
 *
 * <p>L'ordre suit l'énumération du PRD §3, et il compte : c'est cet ordre de déclaration qui
 * tranche les ex-aequo d'une même note dans {@code triggersDetected}. Les motifs sont écrits en
 * minuscules avec leur orthographe accentuée ; {@link RiskCalculator} normalise motifs et texte
 * de note (minuscules + accents retirés) au moment du match, donc l'orthographe canonique reste
 * intacte ici. Pour changer le vocabulaire il faut toucher au code, ce n'est pas de la config.</p>
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
        // juste un porte-constantes, pas la peine de l'instancier
    }
}
