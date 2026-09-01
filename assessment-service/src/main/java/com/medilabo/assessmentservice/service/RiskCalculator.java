package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.dto.RiskResult;
import com.medilabo.assessmentservice.model.RiskBand;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcul de risque déterministe et pur sur les onze termes déclencheurs canoniques.
 *
 * <p>Pas de contexte Spring, pas d'état externe : avec le même {@code (patient, notes, referenceDate)} on retombe toujours sur le même {@link RiskResult}. Les quatre cas de test fournis par le client (voir {@code Documentation/}) servent d'oracle.</p>
 */
public class RiskCalculator {

    /** Motifs du vocabulaire déjà normalisés, indexés par nom canonique. Le vocabulaire est fixe, donc pas de souci de cache. */
    private static final Map<String, List<String>> FOLDED_PATTERNS = TriggerVocabulary.TERMS.stream()
            .collect(Collectors.toUnmodifiableMap(
                    TriggerTerm::canonicalName,
                    term -> term.matchPatterns().stream().map(RiskCalculator::fold).toList()));

    /**
     * Calcule le nombre de déclencheurs, les déclencheurs détectés et la bande de risque.
     *
     * @param notes         les notes du patient (peu importe l'ordre : triées chronologiquement en interne).
     * @param referenceDate date à laquelle l'âge est calculé (injectée plutôt que lue de l'horloge, pour que le calcul reste pur et déterministe).
     * @return nombre de déclencheurs distincts, noms canoniques dans l'ordre du premier match chronologique, et la bande classifiée.
     */
    public RiskResult compute(PatientView patient, List<NoteView> notes, java.time.LocalDate referenceDate) {
        List<String> triggersDetected = detectTriggers(notes);
        int triggerCount = triggersDetected.size();
        RiskBand band = classify(patient.age(referenceDate), patient.gender(), triggerCount);
        return new RiskResult(triggerCount, triggersDetected, band);
    }

    /**
     * Parcourt les notes du plus ancien au plus récent, renvoie chaque terme détecté dans l'ordre du premier match, sans doublon. Dans une même note, l'ordre suit la première apparition textuelle du terme (comme dans l'exemple de la réponse HTTP) ; à position égale, on retombe sur l'ordre de {@code TriggerVocabulary.TERMS}. Le match ignore casse et accents, cherche une sous-chaîne contiguë ; répéter un terme dans une autre note ne le rajoute pas.
     *
     * <p>Si deux notes ont exactement le même {@code createdAt} (à la milliseconde près), on départage sur l'id croissant : c'est un ObjectId Mongo, dont les premiers octets encodent l'instant de création, donc id croissant = ordre d'insertion. Sans ce départage, le tri stable garderait l'ordre du client upstream, qui trie en {@code DESC} et on scannerait la note la plus récente en premier, ce qui fausserait tout.</p>
     */
    private List<String> detectTriggers(List<NoteView> notes) {
        List<NoteView> chronological = new ArrayList<>(notes);
        chronological.sort(Comparator
                .comparing(NoteView::createdAt, Comparator.nullsLast(Instant::compareTo))
                .thenComparing(NoteView::id, Comparator.nullsLast(Comparator.naturalOrder())));

        List<String> detected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (NoteView note : chronological) {
            String haystack = fold(note.note() == null ? "" : note.note());
            for (String canonicalName : newInThisNote(haystack, seen)) {
                detected.add(canonicalName);
                seen.add(canonicalName);
            }
        }
        return List.copyOf(detected);
    }

    /**
     * @return les noms pas encore détectés qui matchent cette note, ordonnés par la position la plus tôt où le motif du terme apparaît dans le texte.
     */
    private List<String> newInThisNote(String foldedNote, Set<String> alreadyDetected) {
        List<int[]> positioned = new ArrayList<>(); // [indexTerme, positionDuPremierMatch]
        for (int i = 0; i < TriggerVocabulary.TERMS.size(); i++) {
            TriggerTerm term = TriggerVocabulary.TERMS.get(i);
            if (alreadyDetected.contains(term.canonicalName())) {
                continue;
            }
            int offset = firstMatchOffset(foldedNote, term);
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
     * @return la position la plus tôt où un des motifs du terme apparaît dans le texte (déjà en minuscules), ou -1 si aucun ne matche.
     */
    private int firstMatchOffset(String foldedNote, TriggerTerm term) {
        int best = -1;
        for (String pattern : FOLDED_PATTERNS.get(term.canonicalName())) {
            int idx = foldedNote.indexOf(pattern);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    /**
     * Normalise un texte pour le matching : minuscules et accents retirés, comme ça "hemoglobine" tapé sans accent matche quand même le terme canonique "Hémoglobine A1C".
     *
     * <p>On recompose en NFC avant de décomposer, pour qu'un texte déjà décomposé (e + accent combinant) folde vers exactement la même chaîne qu'un texte précomposé. Les offsets rendus restent donc comparables entre eux peu importe l'encodage d'entrée et l'ordre de {@code triggersDetected} en dépend. Attention : ce sont des positions dans le texte foldé, plus courtes que dans le texte d'origine s'il y avait des accents, donc ne pas s'en servir pour indexer la note brute.</p>
     */
    private static String fold(String text) {
        String composed = Normalizer.normalize(text, Normalizer.Form.NFC);
        return Normalizer.normalize(composed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Dérive la bande de risque depuis l'âge, le genre et le nombre de déclencheurs, selon la table de règles métier.
     *
     * <p>Les bandes se chevauchent (chez un homme de moins de 30 ans, 5 déclencheurs satisfont à la fois Early Onset et In Danger) et la règle métier veut que la plus sévère l'emporte.
     * D'où l'ordre des tests, de la plus sévère à la moins sévère, avec sortie immédiate : une fois qu'Early Onset matche, rien de pire ne peut suivre, donc on rend la main. <b>Cet ordre porte la règle</b> — permuter deux blocs change le résultat (un homme de 25 ans avec 5 déclencheurs sortirait In Danger). C'est aussi ce qui rend les bornes hautes inutiles : pas besoin d'écrire {@code count <= 5} sur Borderline, les cas au-dessus sont déjà partis.</p>
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

        if ((!over30 && male && count >= 5)
                || (!over30 && female && count >= 7)
                || (over30 && count >= 8)) {
            return RiskBand.EARLY_ONSET;
        }
        if ((!over30 && male && count >= 3)
                || (!over30 && female && count >= 4)
                || (over30 && count >= 6)) {
            return RiskBand.IN_DANGER;
        }
        if (over30 && count >= 2) {
            return RiskBand.BORDERLINE;
        }
        return RiskBand.NONE;
    }
}
