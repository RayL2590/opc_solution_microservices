package com.medilabo.assessmentservice.service;

import com.medilabo.assessmentservice.client.NotesServiceClient;
import com.medilabo.assessmentservice.client.PatientServiceClient;
import com.medilabo.assessmentservice.dto.AssessmentResponseDTO;
import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.dto.RiskResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Orchestre l'évaluation de risque d'un patient : récupère démographie et notes via les
 * clients upstream, lance {@link RiskCalculator}, et mappe le résultat dans l'enveloppe
 * {@link AssessmentResponseDTO}.
 *
 * <p>Chaque appel refait les deux requêtes upstream et recalcule tout, pas de cache entre les
 * requêtes. Du coup une note ajoutée est prise en compte dès la prochaine évaluation.</p>
 */
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final PatientServiceClient patientServiceClient;
    private final NotesServiceClient notesServiceClient;
    private final RiskCalculator riskCalculator = new RiskCalculator();

    /**
     * Calcule l'évaluation de risque courante pour ce patient.
     *
     * @return l'enveloppe FR-8 pour ce patient.
     * @throws com.medilabo.assessmentservice.exception.UpstreamNotFoundException      patient ou notes introuvables upstream.
     * @throws com.medilabo.assessmentservice.exception.BadGatewayException            erreur d'un service upstream.
     * @throws com.medilabo.assessmentservice.exception.GatewayTimeoutException        service upstream injoignable.
     * @throws com.medilabo.assessmentservice.exception.IncompletePatientDataException données démographiques du patient incomplètes.
     */
    public AssessmentResponseDTO assess(Integer patId) {
        LocalDate referenceDate = LocalDate.now();

        PatientView patient = patientServiceClient.getPatient(patId);
        List<NoteView> notes = notesServiceClient.getNotesByPatId(patId);

        RiskResult result = riskCalculator.compute(patient, notes, referenceDate);

        AssessmentResponseDTO.PatientBlock patientBlock = new AssessmentResponseDTO.PatientBlock(
                patient.firstName(), patient.lastName(), patient.age(referenceDate));

        return new AssessmentResponseDTO(
                patId,
                patientBlock,
                result.riskBand(),
                result.triggerCount(),
                result.triggersDetected());
    }
}
