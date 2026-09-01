package com.medilabo.frontservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medilabo.frontservice.client.AssessmentGatewayClient;
import com.medilabo.frontservice.client.NotesGatewayClient;
import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.AssessmentView;
import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Tests d'orchestration pour {@link PatientUiService} : les trois clients Gateway sont mockés, on vérifie les appels délégués, l'assemblage de {@link PatientUiService.PatientDetail} et les champs serveur posés sur {@link NoteForm} avant envoi.
 */
@ExtendWith(MockitoExtension.class)
class PatientUiServiceTest {

    @Mock private PatientGatewayClient patientGatewayClient;
    @Mock private NotesGatewayClient notesGatewayClient;
    @Mock private AssessmentGatewayClient assessmentGatewayClient;

    private PatientUiService patientUiService;

    @BeforeEach
    void setUp() {
        patientUiService = new PatientUiService(
                patientGatewayClient, notesGatewayClient, assessmentGatewayClient);
    }

    private static PatientView patient(Long id, String firstName, String lastName) {
        return new PatientView(id, firstName, lastName,
                LocalDate.of(1966, 12, 31), "F", "1 Brookside St", "+33100222333");
    }

    // ---- liste ----

    @Test
    @DisplayName("getAllPatients delegates to the patient gateway client")
    void getAllPatients_delegatesToClient() {
        List<PatientView> expected = List.of(patient(1L, "Test", "TestNone"), patient(2L, "Jean", "Dupont"));
        when(patientGatewayClient.getAllPatients()).thenReturn(expected);

        assertThat(patientUiService.getAllPatients()).isEqualTo(expected);
        verifyNoInteractions(notesGatewayClient, assessmentGatewayClient);
    }

    @Test
    @DisplayName("getAllPatients passes an empty list through unchanged")
    void getAllPatients_emptyList_returnsEmpty() {
        when(patientGatewayClient.getAllPatients()).thenReturn(List.of());

        assertThat(patientUiService.getAllPatients()).isEmpty();
    }

    // ---- fiche détail ----

    @Test
    @DisplayName("loadPatientDetail assembles patient, notes and assessment from the three clients")
    void loadPatientDetail_assemblesAllThreeUpstreamCalls() {
        PatientView patient = patient(1L, "Test", "TestNone");
        NoteView note = new NoteView("abc123", 1, "TestNone", "Observation clinique.", Instant.now());
        AssessmentView assessment = new AssessmentView("Borderline", 2, List.of("Poids", "Fumeur"));
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient);
        when(notesGatewayClient.getNotesByPatId(1L)).thenReturn(List.of(note));
        when(assessmentGatewayClient.getAssessment(1L)).thenReturn(assessment);

        PatientUiService.PatientDetail detail = patientUiService.loadPatientDetail(1L);

        assertThat(detail.patient()).isEqualTo(patient);
        assertThat(detail.notes()).containsExactly(note);
        assertThat(detail.assessment()).isEqualTo(assessment);
    }

    @Test
    @DisplayName("loadPatientDetail keeps an empty notes list empty")
    void loadPatientDetail_noNotes_returnsEmptyNotes() {
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient(1L, "Test", "TestNone"));
        when(notesGatewayClient.getNotesByPatId(1L)).thenReturn(List.of());
        when(assessmentGatewayClient.getAssessment(1L)).thenReturn(new AssessmentView("None", 0, List.of()));

        PatientUiService.PatientDetail detail = patientUiService.loadPatientDetail(1L);

        assertThat(detail.notes()).isEmpty();
        assertThat(detail.assessment().riskBand()).isEqualTo("None");
    }

    // ---- édition : pré-remplissage du formulaire ----

    @Test
    @DisplayName("getPatient delegates to the patient gateway client")
    void getPatient_delegatesToClient() {
        PatientView patient = patient(7L, "Alice", "Martin");
        when(patientGatewayClient.getPatient(7L)).thenReturn(patient);

        assertThat(patientUiService.getPatient(7L)).isEqualTo(patient);
        verifyNoInteractions(notesGatewayClient, assessmentGatewayClient);
    }

    // ---- création ----

    @Test
    @DisplayName("createPatient returns the created patient from the gateway")
    void createPatient_returnsCreatedPatient() {
        PatientForm form = new PatientForm();
        form.setFirstName("Alice");
        form.setLastName("Martin");
        PatientView created = patient(42L, "Alice", "Martin");
        when(patientGatewayClient.createPatient(form)).thenReturn(created);

        assertThat(patientUiService.createPatient(form)).isEqualTo(created);
    }

    @Test
    @DisplayName("createPatient rejects a null patient returned by the gateway")
    void createPatient_gatewayReturnsNull_throwsIllegalState() {
        PatientForm form = new PatientForm();
        when(patientGatewayClient.createPatient(form)).thenReturn(null);

        assertThatThrownBy(() -> patientUiService.createPatient(form))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("createPatient rejects a created patient with a null id")
    void createPatient_gatewayReturnsNullId_throwsIllegalState() {
        PatientForm form = new PatientForm();
        when(patientGatewayClient.createPatient(form)).thenReturn(
                new PatientView(null, "Alice", "Martin", LocalDate.of(1990, 3, 20), "F", null, null));

        assertThatThrownBy(() -> patientUiService.createPatient(form))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- mise à jour ----

    @Test
    @DisplayName("updatePatient delegates id and form to the gateway client")
    void updatePatient_delegatesToClient() {
        PatientForm form = new PatientForm();
        form.setFirstName("Test");

        patientUiService.updatePatient(1L, form);

        verify(patientGatewayClient).updatePatient(1L, form);
        verifyNoInteractions(notesGatewayClient, assessmentGatewayClient);
    }

    // ---- ajout de note ----

    @Test
    @DisplayName("addNote sets the server-owned patId and patient name before posting the note")
    void addNote_setsServerOwnedFieldsFromPatient() {
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient(1L, "Test", "TestNone"));
        NoteForm form = new NoteForm();
        form.setNote("Observation clinique.");

        patientUiService.addNote(1L, form);

        ArgumentCaptor<NoteForm> captor = ArgumentCaptor.forClass(NoteForm.class);
        verify(notesGatewayClient).addNote(captor.capture());
        NoteForm posted = captor.getValue();
        assertThat(posted.getPatId()).isEqualTo(1);
        assertThat(posted.getPatient()).isEqualTo("TestNone");
        assertThat(posted.getNote()).isEqualTo("Observation clinique.");
    }

    @Test
    @DisplayName("addNote fails fast when the Gateway returns a patient with no usable lastName")
    void addNote_blankLastName_failsFastWithoutPosting() {
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient(1L, "Test", "  "));
        NoteForm form = new NoteForm();
        form.setNote("Observation clinique.");

        assertThatThrownBy(() -> patientUiService.addNote(1L, form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lastName");

        verifyNoInteractions(notesGatewayClient);
    }

    @Test
    @DisplayName("addNote never overwrites patId/patient with client-supplied values")
    void addNote_ignoresClientSuppliedServerFields() {
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient(1L, "Test", "TestNone"));
        NoteForm form = new NoteForm();
        form.setPatId(999);
        form.setPatient("Spoofed");
        form.setNote("Observation clinique.");

        patientUiService.addNote(1L, form);

        ArgumentCaptor<NoteForm> captor = ArgumentCaptor.forClass(NoteForm.class);
        verify(notesGatewayClient).addNote(captor.capture());
        assertThat(captor.getValue().getPatId()).isEqualTo(1);
        assertThat(captor.getValue().getPatient()).isEqualTo("TestNone");
    }

    @Test
    @DisplayName("addNote does not call the assessment client")
    void addNote_doesNotCallAssessmentClient() {
        when(patientGatewayClient.getPatient(1L)).thenReturn(patient(1L, "Test", "TestNone"));
        NoteForm form = new NoteForm();
        form.setNote("Observation clinique.");

        patientUiService.addNote(1L, form);

        verifyNoInteractions(assessmentGatewayClient);
        verify(notesGatewayClient).addNote(any(NoteForm.class));
    }
}
