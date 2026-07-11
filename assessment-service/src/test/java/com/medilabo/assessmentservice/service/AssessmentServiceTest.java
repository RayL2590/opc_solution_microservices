package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.client.NotesServiceClient;
import com.medilabo.assessmentservice.client.PatientServiceClient;
import com.medilabo.assessmentservice.dto.AssessmentResponseDTO;
import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.IncompletePatientDataException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests d'orchestration pour {@link AssessmentService} : clients 4.2 mockés, vrai
 * {@link RiskCalculator}, vérifie le mapping FR-8 et reproduit les quatre fixtures
 * canoniques du Sprint 3 (oracle SM-2) au-dessus du niveau algo pur.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    /** "Today" figé pour que les fixtures/assertions basées sur l'âge ne dérivent pas à minuit. */
    private static final LocalDate TODAY = LocalDate.now();

    @Mock private PatientServiceClient patientServiceClient;
    @Mock private NotesServiceClient notesServiceClient;

    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        assessmentService = new AssessmentService(patientServiceClient, notesServiceClient);
    }

    private static NoteView note(Integer patId, String patient, String text, Instant createdAt) {
        return new NoteView(null, patId, patient, text, createdAt);
    }

    private static Instant at(int hour) {
        return Instant.parse(String.format("2024-01-10T%02d:00:00Z", hour));
    }

    // ---- mapping de l'enveloppe FR-8 ----

    @Test
    @DisplayName("happy path maps patient + risk result into the FR-8 envelope")
    void assess_happyPath_returnsFr8Envelope() {
        PatientView patient = new PatientView(4, "TestEarlyOnset", "Patient4",
                TODAY.minusYears(23), "F");
        List<NoteView> notes = List.of(note(4, "Patient4", "Poids", at(8)));

        when(patientServiceClient.getPatient(4)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(4)).thenReturn(notes);

        AssessmentResponseDTO result = assessmentService.assess(4);

        assertThat(result.patId()).isEqualTo(4);
        assertThat(result.patient().firstName()).isEqualTo("TestEarlyOnset");
        assertThat(result.patient().lastName()).isEqualTo("Patient4");
        assertThat(result.patient().age()).isEqualTo(23);
        assertThat(result.triggerCount()).isEqualTo(1);
        assertThat(result.triggersDetected()).containsExactly("Poids");
        assertThat(result.riskBand()).isEqualTo("None");
    }

    @Test
    @DisplayName("zero notes yields triggerCount 0, empty triggers, and band None")
    void assess_zeroNotes_returnsNoneBand() {
        PatientView patient = new PatientView(9, "Jean", "Dupont",
                LocalDate.of(1980, 5, 15), "M");
        when(patientServiceClient.getPatient(9)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(9)).thenReturn(List.of());

        AssessmentResponseDTO result = assessmentService.assess(9);

        assertThat(result.triggerCount()).isZero();
        assertThat(result.triggersDetected()).isEmpty();
        assertThat(result.riskBand()).isEqualTo("None");
    }

    // ---- les exceptions cascadent sans être modifiées ----

    @Test
    @DisplayName("patient client 404 propagates as UpstreamNotFoundException")
    void assess_patientNotFound_propagatesException() {
        when(patientServiceClient.getPatient(1)).thenThrow(
                new UpstreamNotFoundException("Patient introuvable : id=1"));

        assertThrows(UpstreamNotFoundException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("patient client 5xx propagates as BadGatewayException")
    void assess_patientBadGateway_propagatesException() {
        when(patientServiceClient.getPatient(1)).thenThrow(
                new BadGatewayException("Erreur upstream patient-service pour id=1"));

        assertThrows(BadGatewayException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("patient client unreachable propagates as GatewayTimeoutException")
    void assess_patientGatewayTimeout_propagatesException() {
        when(patientServiceClient.getPatient(1)).thenThrow(
                new GatewayTimeoutException("patient-service inaccessible pour id=1",
                        new RuntimeException("connection refused")));

        assertThrows(GatewayTimeoutException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("null dateOfBirth propagates as IncompletePatientDataException")
    void assess_incompletePatientData_propagatesException() {
        when(patientServiceClient.getPatient(1)).thenThrow(
                new IncompletePatientDataException(1));

        assertThrows(IncompletePatientDataException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("notes client 404 propagates as UpstreamNotFoundException")
    void assess_notesNotFound_propagatesException() {
        PatientView patient = new PatientView(1, "Jean", "Dupont",
                LocalDate.of(1980, 5, 15), "M");
        when(patientServiceClient.getPatient(1)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(1)).thenThrow(
                new UpstreamNotFoundException("Notes introuvables : patientId=1"));

        assertThrows(UpstreamNotFoundException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("notes client 5xx propagates as BadGatewayException")
    void assess_notesBadGateway_propagatesException() {
        PatientView patient = new PatientView(1, "Jean", "Dupont",
                LocalDate.of(1980, 5, 15), "M");
        when(patientServiceClient.getPatient(1)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(1)).thenThrow(
                new BadGatewayException("Erreur upstream notes-service pour patientId=1"));

        assertThrows(BadGatewayException.class, () -> assessmentService.assess(1));
    }

    @Test
    @DisplayName("notes client unreachable propagates as GatewayTimeoutException")
    void assess_notesGatewayTimeout_propagatesException() {
        PatientView patient = new PatientView(1, "Jean", "Dupont",
                LocalDate.of(1980, 5, 15), "M");
        when(patientServiceClient.getPatient(1)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(1)).thenThrow(
                new GatewayTimeoutException("notes-service inaccessible pour patientId=1",
                        new RuntimeException("connection refused")));

        assertThrows(GatewayTimeoutException.class, () -> assessmentService.assess(1));
    }

    // ---- AC4: les quatre fixtures canoniques (oracle SM-2), reproduites au niveau orchestration ----

    private static Stream<Arguments> canonicalFixtures() {
        PatientView p1 = new PatientView(1, "Test", "TestNone", TODAY.minusYears(58), "F");
        List<NoteView> n1 = List.of(
                note(1, "TestNone",
                        "Le patient déclare qu'il 'se sent très bien' Poids égal ou inférieur au poids recommandé",
                        at(8))
        );

        PatientView p2 = new PatientView(2, "Test", "TestBorderline", TODAY.minusYears(80), "M");
        List<NoteView> n2 = List.of(
                note(2, "TestBorderline",
                        "Le patient déclare qu'il ressent beaucoup de stress au travail Il se plaint également que son audition est anormale dernièrement",
                        at(9)),
                note(2, "TestBorderline",
                        "Le patient déclare avoir fait une réaction aux médicaments au cours des 3 derniers mois Il remarque également que son audition continue d'être anormale",
                        at(10))
        );

        PatientView p3 = new PatientView(3, "Test", "TestInDanger", TODAY.minusYears(21), "M");
        List<NoteView> n3 = List.of(
                note(3, "TestInDanger", "Le patient déclare qu'il fume depuis peu", at(9)),
                note(3, "TestInDanger",
                        "Le patient déclare qu'il est fumeur et qu'il a cessé de fumer l'année dernière Il se plaint également de crises d'apnée respiratoire anormales Tests de laboratoire indiquant un taux de cholestérol LDL élevé",
                        at(10))
        );

        PatientView p4 = new PatientView(4, "Test", "TestEarlyOnset", TODAY.minusYears(23), "F");
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
                Arguments.of(1, p1, n1, 1, "None"),
                Arguments.of(2, p2, n2, 2, "Borderline"),
                Arguments.of(3, p3, n3, 3, "In Danger"),
                Arguments.of(4, p4, n4, 7, "Early Onset")
        );
    }

    @ParameterizedTest(name = "patId {0} -> count {3}, band {4}")
    @MethodSource("canonicalFixtures")
    @DisplayName("AC4 — the four canonical fixtures resolve to the expected band and count via assess()")
    void assess_canonicalFixtures_resolveExpectedBandAndCount(
            Integer patId, PatientView patient, List<NoteView> notes,
            int expectedCount, String expectedBand) {

        when(patientServiceClient.getPatient(patId)).thenReturn(patient);
        when(notesServiceClient.getNotesByPatId(patId)).thenReturn(notes);

        AssessmentResponseDTO result = assessmentService.assess(patId);

        assertThat(result.patId()).isEqualTo(patId);
        assertThat(result.triggerCount()).isEqualTo(expectedCount);
        assertThat(result.riskBand()).isEqualTo(expectedBand);
        assertThat(result.patient().age())
                .isEqualTo(Period.between(patient.dateOfBirth(), TODAY).getYears());
    }
}
