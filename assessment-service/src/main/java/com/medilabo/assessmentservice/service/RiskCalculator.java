package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.dto.RiskResult;
import com.medilabo.assessmentservice.model.RiskBand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Calcul de risque déterministe et pur sur les onze termes déclencheurs canoniques (FR-9).
 *
 * <p>Pas de contexte Spring, pas d'état externe : le même {@code (patient, notes, today)}
 * donne toujours le même {@link RiskResult}. Les quatre fixtures canoniques du Sprint 3
 * servent d'oracle (SM-2). Matching, comptage et classification suivent les règles du
 * PRD §4.3 FR-9.</p>
 */
public class RiskCalculator {

    /**
     * Calcule le nombre de déclencheurs, les déclencheurs détectés et la bande de risque.
     *
     * @param notes   les notes du patient (peu importe l'ordre — triées chronologiquement en interne).
     * @param today   date de référence pour l'âge (explicite pour rester déterministe).
     * @return nombre de déclencheurs distincts, noms canoniques dans l'ordre du premier match
     *         chronologique, et la bande classifiée.
     */
    public RiskResult compute(PatientView patient, List<NoteView> notes, java.time.LocalDate today) {
        List<String> triggersDetected = detectTriggers(notes);
        int triggerCount = triggersDetected.size();
        RiskBand band = classify(patient.age(today), patient.gender(), triggerCount);
        return new RiskResult(triggerCount, triggersDetected, band);
    }

    /**
     * Parcourt les notes du plus ancien au plus récent, renvoie chaque terme détecté dans
     * l'ordre du premier match, sans doublon. Dans une même note, l'ordre suit la première
     * apparition textuelle du terme (comme l'exemple de l'enveloppe FR-8) ; à position égale,
     * on retombe sur l'ordre de {@code TriggerVocabulary.TERMS}. Match insensible à la casse,
     * sous-chaîne contiguë (accents conservés) ; répéter un terme dans une autre note ne le
     * rajoute pas.
     */
    private List<String> detectTriggers(List<NoteView> notes) {
        List<NoteView> chronological = new ArrayList<>(notes);
        chronological.sort(Comparator.comparing(NoteView::createdAt, Comparator.nullsLast(Instant::compareTo)));

        List<String> detected = new ArrayList<>();
        for (NoteView note : chronological) {
            String haystack = note.note() == null ? "" : note.note().toLowerCase(Locale.ROOT);
            newInThisNote(haystack, detected).forEach(detected::add);
        }
        return List.copyOf(detected);
    }

    /**
     * @return les noms pas encore détectés qui matchent cette note, ordonnés par la position
     *         la plus tôt où le motif du terme apparaît dans le texte.
     */
    private List<String> newInThisNote(String lowercasedNote, List<String> alreadyDetected) {
        List<int[]> positioned = new ArrayList<>(); // [indexTerme, positionPremierMatch]
        for (int i = 0; i < TriggerVocabulary.TERMS.size(); i++) {
            TriggerTerm term = TriggerVocabulary.TERMS.get(i);
            if (alreadyDetected.contains(term.canonicalName())) {
                continue;
            }
            int offset = firstMatchOffset(lowercasedNote, term);
            if (offset >= 0) {
                positioned.add(new int[]{i, offset});
            }
        }
        positioned.sort(Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));

        List<String> result = new ArrayList<>();
        for (int[] p : positioned) {
            result.add(TriggerVocabulary.TERMS.get(p[0]).canonicalName());
        }
        return result;
    }

    /**
     * @return la position la plus tôt où un des motifs du terme apparaît dans le texte (déjà
     *         en minuscules), ou -1 si aucun ne matche.
     */
    private int firstMatchOffset(String lowercasedNote, TriggerTerm term) {
        int best = -1;
        for (String pattern : term.matchPatterns()) {
            int idx = lowercasedNote.indexOf(pattern);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    /**
     * Dérive la bande de risque depuis l'âge, le genre et le nombre de déclencheurs, selon la
     * table de règles FR-9. Toutes les branches sont évaluées, la plus sévère qui matche gagne
     * (max sur l'ordinal).
     *
     * @param gender code sur un caractère, comparé insensible à la casse, {@code M}/{@code F}.
     * @param count  nombre de déclencheurs distincts, [0, 11].
     * @return la bande classifiée ; {@link RiskBand#NONE} si aucune branche ne matche.
     */
    private RiskBand classify(int age, String gender, int count) {
        boolean over30 = age > 30;
        boolean male = gender != null && !gender.isBlank()
                && Character.toUpperCase(gender.charAt(0)) == 'M';
        boolean female = gender != null && !gender.isBlank()
                && Character.toUpperCase(gender.charAt(0)) == 'F';

        RiskBand band = RiskBand.NONE;

        if (count >= 2 && over30 && count <= 5) {
            band = highest(band, RiskBand.BORDERLINE);
        }
        boolean inDanger =
                (!over30 && male && count >= 3)
                        || (!over30 && female && count >= 4)
                        || (over30 && count >= 6 && count <= 7);
        if (inDanger) {
            band = highest(band, RiskBand.IN_DANGER);
        }
        boolean earlyOnset =
                (!over30 && male && count >= 5)
                        || (!over30 && female && count >= 7)
                        || (over30 && count >= 8);
        if (earlyOnset) {
            band = highest(band, RiskBand.EARLY_ONSET);
        }

        // count <= 1 ne matche jamais aucune branche au-dessus, donc reste à NONE.
        return band;
    }

    private RiskBand highest(RiskBand a, RiskBand b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
