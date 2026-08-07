package com.medilabo.frontservice.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.medilabo.frontservice.client.AssessmentGatewayClient;
import com.medilabo.frontservice.client.NotesGatewayClient;
import com.medilabo.frontservice.client.PatientGatewayClient;
import com.medilabo.frontservice.dto.AssessmentView;
import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;
import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Orchestre les appels Gateway pour les écrans patients : démographie, notes et évaluation
 * de risque. Le contrôleur ne garde que le binding HTTP et le choix de vue.
 *
 * <p>Pas de cache : chaque appel refait les requêtes upstream, donc une note ajoutée
 * apparaît dès le rendu suivant.</p>
 *
 * <p>PII : seuls les ids et compteurs sont loggés.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PatientUiService {

    private final PatientGatewayClient patientGatewayClient;
    private final NotesGatewayClient notesGatewayClient;
    private final AssessmentGatewayClient assessmentGatewayClient;

    /** Données composites de la fiche détail : démographie, timeline des notes, évaluation de risque. */
    public record PatientDetail(PatientView patient, List<NoteView> notes, AssessmentView assessment) {}

    public List<PatientView> getAllPatients() {
        List<PatientView> patients = patientGatewayClient.getAllPatients();
        log.debug("Loaded patient list, count={}", patients.size());
        return patients;
    }

    public PatientView getPatient(Long id) {
        return patientGatewayClient.getPatient(id);
    }

    /** Assemble les trois appels upstream nécessaires à la fiche détail. */
    public PatientDetail loadPatientDetail(Long id) {
        PatientView patient = patientGatewayClient.getPatient(id);
        List<NoteView> notes = notesGatewayClient.getNotesByPatId(id);
        AssessmentView assessment = assessmentGatewayClient.getAssessment(id);
        log.debug("Loaded patient detail, id={}, noteCount={}", id, notes.size());
        return new PatientDetail(patient, notes, assessment);
    }

    /**
     * @throws IllegalStateException si la Gateway renvoie un patient nul ou sans id — le
     *         contrôleur ne peut alors pas construire sa redirection.
     */
    public PatientView createPatient(PatientForm form) {
        PatientView created = patientGatewayClient.createPatient(form);
        if (created == null || created.id() == null) {
            throw new IllegalStateException(
                    "Gateway returned a null patient or null id after creation — cannot redirect");
        }
        log.debug("Patient created, id={}", created.id());
        return created;
    }

    public PatientView updatePatient(Long id, PatientForm form) {
        PatientView updated = patientGatewayClient.updatePatient(id, form);
        log.debug("Patient updated, id={}", id);
        return updated;
    }

    /**
     * Ajoute une note au patient {@code id}. Les champs serveur ({@code patId}, {@code patient})
     * sont écrasés depuis la démographie chargée, jamais pris du formulaire.
     *
     * @throws IllegalStateException si la Gateway renvoie un patient sans nom — notes-service
     *         exige un nom non vide sur chaque note (dénormalisation délibérée du nom du
     *         patient sur chaque note). Autant échouer ici avec une cause claire plutôt que
     *         laisser remonter un 400 venu d'un autre service.
     */
    public NoteView addNote(Long id, NoteForm noteForm) {
        PatientView patient = patientGatewayClient.getPatient(id);
        if (patient == null || patient.lastName() == null || patient.lastName().isBlank()) {
            throw new IllegalStateException(
                    "Gateway returned a patient with no lastName for id=" + id
                            + " — cannot denormalize it onto the note");
        }
        noteForm.setPatId(Math.toIntExact(id));
        noteForm.setPatient(patient.lastName());
        NoteView created = notesGatewayClient.addNote(noteForm);
        log.debug("Note added, patId={}", id);
        return created;
    }
}
