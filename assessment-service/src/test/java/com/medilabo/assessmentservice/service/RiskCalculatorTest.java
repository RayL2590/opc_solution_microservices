package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.dto.RiskResult;
import com.medilabo.assessmentservice.model.RiskBand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs pour {@link RiskCalculator} — pas de contexte Spring. Les quatre
 * fixtures canoniques du Sprint 3 (oracle SM-2) tournent sur le vrai texte de notes du
 * Sprint 2 ({@code docker/mongo-init.js}) ; les âges sont figés via {@link #REFERENCE_DATE}
 * pour que la suite reste déterministe dans le temps.
 */
class RiskCalculatorTest {

    /** "Today" figé pour que les âges des fixtures ne bougent pas : P3→21, P4→23 (≤30), P1→58, P2→80 (>30). */
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 6, 30);

    private final RiskCalculator calculator = new RiskCalculator();

    private static NoteView note(Integer patId, String patient, String text, Instant createdAt) {
        return new NoteView(null, patId, patient, text, createdAt);
    }

    private static Instant at(int hour) {
        return Instant.parse(String.format("2024-01-10T%02d:00:00Z", hour));
    }

    // ---- AC2: les quatre fixtures canoniques (oracle SM-2) ----

    private static Stream<Arguments> canonicalFixtures() {
        PatientView p1 = new PatientView(1, "Test", "TestNone", LocalDate.of(1966, 12, 31), "F");
        List<NoteView> n1 = List.of(
                note(1, "TestNone",
                        "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé",
                        at(8))
        );

        PatientView p2 = new PatientView(2, "Test", "TestBorderline", LocalDate.of(1945, 6, 24), "M");
        List<NoteView> n2 = List.of(
                note(2, "TestBorderline",
                        "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement",
                        at(9)),
                note(2, "TestBorderline",
                        "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale",
                        at(10))
        );

        PatientView p3 = new PatientView(3, "Test", "TestInDanger", LocalDate.of(2004, 6, 18), "M");
        List<NoteView> n3 = List.of(
                note(3, "TestInDanger", "Le patient déclare qu'il fume depuis peu", at(9)),
                note(3, "TestInDanger",
                        "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé",
                        at(10))
        );

        PatientView p4 = new PatientView(4, "Test", "TestEarlyOnset", LocalDate.of(2002, 6, 28), "F");
        List<NoteView> n4 = List.of(
                note(4, "TestEarlyOnset",
                        "Le patient déclare qu'il lui est devenu difficile de monter les escaliers Il se plaint également d'être essoufflé Tests de laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments",
                        at(9)),
                note(4, "TestEarlyOnset",
                        "Le patient déclare qu'il a mal au dos lorsqu'il reste assis pendant longtemps",
                        at(10)),
                note(4, "TestEarlyOnset",
                        "Le patient déclare avoir commencé à fumer depuis peu Hémoglobine A1C supérieure au niveau recommandé",
                        at(11)),
                note(4, "TestEarlyOnset", "Taille, Poids, Cholestérol, Vertige et Réaction", at(12))
        );

        return Stream.of(
                Arguments.of(p1, n1, 1, RiskBand.NONE,
                        List.of("Poids")),
                Arguments.of(p2, n2, 2, RiskBand.BORDERLINE,
                        List.of("Anormal", "Réaction")),
                Arguments.of(p3, n3, 3, RiskBand.IN_DANGER,
                        List.of("Fumeur", "Anormal", "Cholestérol")),
                Arguments.of(p4, n4, 7, RiskBand.EARLY_ONSET,
                        List.of("Anticorps", "Réaction", "Hémoglobine A1C", "Taille", "Poids", "Cholestérol", "Vertiges"))
        );
    }

    @ParameterizedTest(name = "patId {0} → count {2}, band {3}")
    @MethodSource("canonicalFixtures")
    @DisplayName("AC2 — the four canonical fixtures resolve to the expected band and count")
    void canonicalFixtures_resolveExpectedBandAndCount(
            PatientView patient, List<NoteView> notes, int expectedCount,
            RiskBand expectedBand, List<String> expectedTriggers) {

        RiskResult result = calculator.compute(patient, notes, REFERENCE_DATE);

        assertThat(result.triggerCount()).isEqualTo(expectedCount);
        assertThat(result.riskBand()).isEqualTo(expectedBand);
        assertThat(result.triggersDetected()).containsExactlyElementsOf(expectedTriggers);
    }

    // ---- AC1: forme du vocabulaire ----

    @Test
    @DisplayName("AC1 — vocabulary holds 11 terms in PRD order, lowercased, only Fumeur has 2 patterns")
    void vocabulary_hasElevenTermsInOrder() {
        assertThat(TriggerVocabulary.TERMS).hasSize(11);
        assertThat(TriggerVocabulary.TERMS.stream().map(TriggerTerm::canonicalName))
                .containsExactly("Hémoglobine A1C", "Microalbumine", "Taille", "Poids",
                        "Fumeur", "Anormal", "Cholestérol", "Vertiges", "Rechute",
                        "Réaction", "Anticorps");

        for (TriggerTerm term : TriggerVocabulary.TERMS) {
            for (String pattern : term.matchPatterns()) {
                assertThat(pattern).isEqualTo(pattern.toLowerCase(java.util.Locale.ROOT));
            }
        }

        assertThat(TriggerVocabulary.TERMS.stream()
                .filter(t -> t.matchPatterns().size() > 1))
                .singleElement()
                .extracting(TriggerTerm::canonicalName)
                .isEqualTo("Fumeur");
    }

    // ---- AC3: matching des variantes ----

    @Test
    @DisplayName("AC3 — substring rule matches inflections (anormales, cholestérol LDL, Vertige)")
    void substringRule_matchesInflections() {
        PatientView p = new PatientView(9, "T", "T", LocalDate.of(1980, 1, 1), "F");
        List<NoteView> notes = List.of(
                note(9, "T", "résultats anormales et cholestérol LDL élevé, Vertige signalé", at(9))
        );

        RiskResult result = calculator.compute(p, notes, REFERENCE_DATE);

        assertThat(result.triggersDetected())
                .containsExactly("Anormal", "Cholestérol", "Vertiges");
    }

    // ---- AC4: idempotence du comptage & plancher None ----

    @Test
    @DisplayName("AC4 — a term repeated across notes counts once")
    void repeatedTerm_countsOnce() {
        PatientView p = new PatientView(9, "T", "T", LocalDate.of(1980, 1, 1), "F");
        List<NoteView> notes = List.of(
                note(9, "T", "poids élevé", at(9)),
                note(9, "T", "poids toujours élevé", at(10)),
                note(9, "T", "poids stable", at(11))
        );

        RiskResult result = calculator.compute(p, notes, REFERENCE_DATE);

        assertThat(result.triggerCount()).isEqualTo(1);
        assertThat(result.triggersDetected()).containsExactly("Poids");
    }

    @Test
    @DisplayName("AC4 — count of 0 or 1 is None regardless of age/gender")
    void lowCount_isNone() {
        PatientView youngMale = new PatientView(9, "T", "T", LocalDate.of(2010, 1, 1), "M");
        assertThat(calculator.compute(youngMale, List.of(), REFERENCE_DATE).riskBand())
                .isEqualTo(RiskBand.NONE);

        List<NoteView> oneTrigger = List.of(note(9, "T", "poids élevé", at(9)));
        assertThat(calculator.compute(youngMale, oneTrigger, REFERENCE_DATE).riskBand())
                .isEqualTo(RiskBand.NONE);
    }

    // ---- AC5: la bande la plus haute gagne ----

    @Test
    @DisplayName("AC5 — an over-30 patient with 8 triggers is Early Onset, not In Danger")
    void overlap_highestBandWins() {
        PatientView older = new PatientView(9, "T", "T", LocalDate.of(1970, 1, 1), "M");
        List<NoteView> notes = List.of(note(9, "T",
                "hémoglobine a1c microalbumine taille poids fumeur anormal cholestérol vertige", at(9)));

        RiskResult result = calculator.compute(older, notes, REFERENCE_DATE);

        assertThat(result.triggerCount()).isEqualTo(8);
        assertThat(result.riskBand()).isEqualTo(RiskBand.EARLY_ONSET);
    }

    // ---- AC6: triggersDetected suit l'ordre chronologique, peu importe l'ordre de la liste ----

    @Test
    @DisplayName("AC6 — triggersDetected follows chronological first-match order regardless of list order")
    void triggersDetected_isChronologicalRegardlessOfListOrder() {
        PatientView p = new PatientView(9, "T", "T", LocalDate.of(1980, 1, 1), "F");
        NoteView newer = note(9, "T", "anticorps élevés", at(11));
        NoteView older = note(9, "T", "poids et taille", at(9));

        // on passe newest-first (comme le ferait une lecture DESC) ; la détection doit quand même
        // partir du plus ancien, et dans la note ancienne l'ordre suit le texte ("poids" avant "taille")
        RiskResult result = calculator.compute(p, List.of(newer, older), REFERENCE_DATE);

        assertThat(result.triggersDetected()).containsExactly("Poids", "Taille", "Anticorps");
    }

    // ---- Helpers pour construire un nombre exact de déclencheurs depuis le vocabulaire ----

    /** Un match distinct par terme canonique, dans l'ordre de TERMS (index 4 = Fumeur). */
    private static final List<String> ONE_PER_TERM = List.of(
            "hémoglobine a1c", "microalbumine", "taille", "poids", "fumeur",
            "anormal", "cholestérol", "vertige", "rechute", "réaction", "anticorps");

    /** Une seule note dont le texte déclenche exactement les {@code n} premiers termes. */
    private static NoteView noteWithTriggers(int n) {
        return note(9, "T", String.join(" ", ONE_PER_TERM.subList(0, n)), at(9));
    }

    private static PatientView patient(LocalDate dob, String gender) {
        return new PatientView(9, "T", "T", dob, gender);
    }

    /** Une date de naissance donnant l'âge demandé à REFERENCE_DATE (anniversaire déjà passé cette année-là). */
    private static LocalDate dobForAge(int age) {
        return REFERENCE_DATE.minusYears(age).minusDays(1);
    }

    // ---- F3: la borne age==30 inclusive (PRD §9) est verrouillée ----

    @Test
    @DisplayName("F3 — age exactly 30 takes the age<=30 arms (inclusive), M/count=5 → Early Onset")
    void ageExactly30_isInclusive_male() {
        RiskResult r = calculator.compute(patient(dobForAge(30), "M"), List.of(noteWithTriggers(5)), REFERENCE_DATE);
        assertThat(r.triggerCount()).isEqualTo(5);
        // age==30 & M & count>=5 → Early Onset via la branche age<=30 ; un refactor qui glisse
        // vers `>=30` retomberait sur les branches age>30 (count=5 → Borderline) et casserait ce test.
        assertThat(r.riskBand()).isEqualTo(RiskBand.EARLY_ONSET);
    }

    @Test
    @DisplayName("F3 — age exactly 30 takes the age<=30 arms (inclusive), F/count=4 → In Danger")
    void ageExactly30_isInclusive_female() {
        RiskResult r = calculator.compute(patient(dobForAge(30), "F"), List.of(noteWithTriggers(4)), REFERENCE_DATE);
        assertThat(r.triggerCount()).isEqualTo(4);
        assertThat(r.riskBand()).isEqualTo(RiskBand.IN_DANGER);
    }

    // ---- F4: genre null avec un count élevé donne None (contrat : ni branche M ni F) ----

    @Test
    @DisplayName("F4 — gender null, age<=30, count=10 → None (neither M nor F arm applies)")
    void genderNull_youngHighCount_isNone() {
        // Contrat FR-9 : tout ce qui n'est ni M ni F ne prend aucune des deux branches, et les
        // branches age>30 ne s'appliquent pas si age<=30 → None. Documenté ici pour que ce soit
        // un choix assumé, pas un trou accidentel. L'intégrité du genre est gérée en amont (4.2).
        RiskResult r = calculator.compute(patient(dobForAge(25), null), List.of(noteWithTriggers(10)), REFERENCE_DATE);
        assertThat(r.triggerCount()).isEqualTo(10);
        assertThat(r.riskBand()).isEqualTo(RiskBand.NONE);
    }

    // ---- F5: bornes de la table age>30, isolées ----

    @Test
    @DisplayName("F5 — age>30 boundaries: 5→Borderline, 6→In Danger, 7→In Danger, 8→Early Onset")
    void over30_tableBoundaries() {
        PatientView p = patient(dobForAge(45), "M"); // le genre n'entre pas en jeu sur les branches age>30
        assertThat(calculator.compute(p, List.of(noteWithTriggers(5)), REFERENCE_DATE).riskBand())
                .as("count=5, top of [2,5]").isEqualTo(RiskBand.BORDERLINE);
        assertThat(calculator.compute(p, List.of(noteWithTriggers(6)), REFERENCE_DATE).riskBand())
                .as("count=6, bottom of [6,7]").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(p, List.of(noteWithTriggers(7)), REFERENCE_DATE).riskBand())
                .as("count=7, top of [6,7]").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(p, List.of(noteWithTriggers(8)), REFERENCE_DATE).riskBand())
                .as("count=8, >=8").isEqualTo(RiskBand.EARLY_ONSET);
    }

    // ---- F6: seuils M/F pour age<=30 ----

    @Test
    @DisplayName("F6 — age<=30 M thresholds: 3→In Danger, 4→In Danger, 5→Early Onset")
    void young_maleThresholds() {
        PatientView m = patient(dobForAge(25), "M");
        assertThat(calculator.compute(m, List.of(noteWithTriggers(3)), REFERENCE_DATE).riskBand())
                .as("M count=3 floor").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(m, List.of(noteWithTriggers(4)), REFERENCE_DATE).riskBand())
                .as("M count=4 still In Danger (Early Onset floor is 5)").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(m, List.of(noteWithTriggers(5)), REFERENCE_DATE).riskBand())
                .as("M count=5 Early Onset").isEqualTo(RiskBand.EARLY_ONSET);
    }

    @Test
    @DisplayName("F6 — age<=30 F thresholds: 4→In Danger, 6→In Danger, 7→Early Onset")
    void young_femaleThresholds() {
        PatientView f = patient(dobForAge(25), "F");
        assertThat(calculator.compute(f, List.of(noteWithTriggers(4)), REFERENCE_DATE).riskBand())
                .as("F count=4 floor In Danger").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(f, List.of(noteWithTriggers(6)), REFERENCE_DATE).riskBand())
                .as("F count=6 still In Danger (Early Onset floor is 7)").isEqualTo(RiskBand.IN_DANGER);
        assertThat(calculator.compute(f, List.of(noteWithTriggers(7)), REFERENCE_DATE).riskBand())
                .as("F count=7 Early Onset").isEqualTo(RiskBand.EARLY_ONSET);
    }

    @Test
    @DisplayName("F6 — F count=3 is not yet In Danger (F floor is 4, unlike M)")
    void young_female_belowFloor() {
        PatientView f = patient(dobForAge(25), "F");
        // count=3 : aucune branche age<=30 ne matche (M demande >=3 mais c'est F ; F demande >=4),
        // et pas de branche age>30. Si les seuils M/F étaient inversés ça deviendrait In Danger à tort.
        assertThat(calculator.compute(f, List.of(noteWithTriggers(3)), REFERENCE_DATE).riskBand())
                .isEqualTo(RiskBand.NONE);
    }

    // ---- F7: plafond du count (11) et triggersDetected vide ----

    @Test
    @DisplayName("F7 — all eleven terms present → count=11 (ceiling)")
    void allElevenTriggers_countIs11() {
        RiskResult r = calculator.compute(patient(dobForAge(45), "M"), List.of(noteWithTriggers(11)), REFERENCE_DATE);
        assertThat(r.triggerCount()).isEqualTo(11);
        assertThat(r.triggersDetected()).hasSize(11);
        assertThat(r.riskBand()).isEqualTo(RiskBand.EARLY_ONSET);
    }

    @Test
    @DisplayName("F7 — notes with no trigger terms → empty triggersDetected, count=0, None")
    void notesWithoutTriggers_emptyDetected() {
        List<NoteView> notes = List.of(note(9, "T", "le patient va bien, rien à signaler", at(9)));
        RiskResult r = calculator.compute(patient(dobForAge(45), "M"), notes, REFERENCE_DATE);
        assertThat(r.triggerCount()).isZero();
        assertThat(r.triggersDetected()).isEmpty();
        assertThat(r.riskBand()).isEqualTo(RiskBand.NONE);
    }

    // ---- F8: pureté — la liste de l'appelant n'est pas modifiée ----

    @Test
    @DisplayName("F8 — compute does not mutate the caller's notes list (purity)")
    void compute_doesNotMutateInput() {
        NoteView newer = note(9, "T", "anticorps", at(11));
        NoteView older = note(9, "T", "poids", at(9));
        List<NoteView> input = new java.util.ArrayList<>(List.of(newer, older)); // mutable, newest-first
        List<NoteView> snapshot = List.copyOf(input);

        calculator.compute(patient(dobForAge(45), "F"), input, REFERENCE_DATE);

        // un tri in-place dans compute() réordonnerait ça et ferait échouer l'assertion
        assertThat(input).containsExactlyElementsOf(snapshot);
    }

    // ---- F9: entrées dégradées tolérées (createdAt null, texte de note null) ----

    @Test
    @DisplayName("F9 — a note with null createdAt is sorted last, others keep chronological order")
    void nullCreatedAt_sortedLast() {
        NoteView noTimestamp = note(9, "T", "anticorps", null);
        NoteView early = note(9, "T", "poids", at(9));
        NoteView late = note(9, "T", "taille", at(10));

        RiskResult r = calculator.compute(patient(dobForAge(45), "F"),
                List.of(noTimestamp, early, late), REFERENCE_DATE);

        // early → poids, late → taille, note sans timestamp en dernier → anticorps
        assertThat(r.triggersDetected()).containsExactly("Poids", "Taille", "Anticorps");
    }

    @Test
    @DisplayName("F9 — a note with null text is skipped without error")
    void nullNoteText_isSkipped() {
        List<NoteView> notes = List.of(
                note(9, "T", null, at(9)),
                note(9, "T", "poids", at(10)));
        RiskResult r = calculator.compute(patient(dobForAge(45), "F"), notes, REFERENCE_DATE);
        assertThat(r.triggerCount()).isEqualTo(1);
        assertThat(r.triggersDetected()).containsExactly("Poids");
    }

    // ---- F12: les quatre chaînes FR-8 sont verrouillées (utilisées par l'enveloppe Story 4.3) ----

    @Test
    @DisplayName("F12 — RiskBand display names match the exact FR-8 wire strings")
    void riskBand_displayNames_matchFr8Wire() {
        assertThat(RiskBand.NONE.getDisplayName()).isEqualTo("None");
        assertThat(RiskBand.BORDERLINE.getDisplayName()).isEqualTo("Borderline");
        assertThat(RiskBand.IN_DANGER.getDisplayName()).isEqualTo("In Danger");
        assertThat(RiskBand.EARLY_ONSET.getDisplayName()).isEqualTo("Early Onset");
    }
}
