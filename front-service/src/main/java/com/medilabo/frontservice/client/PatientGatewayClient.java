package com.medilabo.frontservice.client;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medilabo.frontservice.dto.PatientForm;
import com.medilabo.frontservice.dto.PatientView;

/**
 * Client Gateway pour les patients. Credential forwarding géré par CredentialForwardingInitializer.
 * PII : seuls les comptes et ids sont loggés — jamais les noms, adresses, téléphones.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatientGatewayClient {

    private final RestClient gatewayClient;

    public List<PatientView> getAllPatients() {
        List<PatientView> patients = gatewayClient.get()
                .uri("/patients")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        log.debug("Fetched {} patient(s) from Gateway", patients != null ? patients.size() : 0);
        return patients != null ? patients : List.of();
    }

    public PatientView createPatient(PatientForm form) {
        PatientView created = gatewayClient.post()
                .uri("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(PatientView.class);
        log.debug("Created patient id={}", created != null ? created.id() : null);
        return created;
    }

    public PatientView getPatient(Long id) {
        PatientView patient = gatewayClient.get()
                .uri("/patients/{id}", id)
                .retrieve()
                .body(PatientView.class);
        log.debug("Fetched patient id={}", id);
        return patient;
    }

    public PatientView updatePatient(Long id, PatientForm form) {
        PatientView updated = gatewayClient.put()
                .uri("/patients/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(PatientView.class);
        log.debug("Updated patient id={}", id);
        return updated;
    }
}
