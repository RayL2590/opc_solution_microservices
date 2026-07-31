package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.PatientView;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.IncompletePatientDataException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Client upstream pour les données démographiques patient (Gateway → patient-service).
 * L'authentification sortante passe par ServiceAccountAuthInitializer (compte de service).
 * PII : seul patientId finit dans les logs, jamais le nom ni l'adresse.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatientServiceClient {

    private final RestClient gatewayClient;

    /**
     * Récupère un patient par son id et le mappe en {@link PatientView}.
     *
     * @return PatientView avec dateOfBirth non-null (contrat F1)
     * @throws UpstreamNotFoundException      patient-service a renvoyé 404
     * @throws BadGatewayException            patient-service a renvoyé 5xx
     * @throws GatewayTimeoutException        connexion refusée ou expirée
     * @throws IncompletePatientDataException dateOfBirth null dans la réponse (422)
     */
    public PatientView getPatient(Integer patientId) {
        log.debug("Fetching patient id={} from upstream", patientId);
        try {
            PatientView patient = gatewayClient.get()
                    .uri("/patients/{id}", patientId)
                    .retrieve()
                    .body(PatientView.class);

            // Corps absent ou dateOfBirth absente, même traitement : dans les deux cas il manque
            // la seule donnée sans laquelle on ne peut rien calculer (age() partirait en NPE).
            // Pas un 404 ceci dit — patient-service a répondu 2xx, la ressource existe, elle est
            // juste inexploitable. D'où le 422 plutôt qu'un UpstreamNotFoundException.
            // Les autres champs, on ne les vérifie pas ici : NOT NULL en base + @NotBlank sur
            // PatientDTO côté patient-service, et gender est déjà géré null-safe dans RiskCalculator.
            if (patient == null || patient.dateOfBirth() == null) {
                throw new IncompletePatientDataException(patientId);
            }
            log.debug("Fetched patient id={}", patientId);
            return patient;

        } catch (HttpClientErrorException.NotFound e) {
            throw new UpstreamNotFoundException("Patient introuvable : id=" + patientId, e);
        } catch (HttpServerErrorException e) {
            throw new BadGatewayException(
                    "Erreur upstream patient-service pour id=" + patientId, e);
        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException(
                    "patient-service inaccessible pour id=" + patientId, e);
        }
    }
}
