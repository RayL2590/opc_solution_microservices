package com.medilabo.frontservice.client;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medilabo.frontservice.dto.PatientView;

/**
 * Gateway client for patient operations (Story 5.2, FR-10).
 *
 * <p>Wraps the {@code gatewayClient} {@link RestClient} bean from
 * {@code RestClientConfig} (Story 5.1) — no new {@code RestClient} is created here.
 * Credential forwarding is handled transparently by {@code CredentialForwardingInitializer};
 * every call to this component automatically carries the inbound {@code Authorization} header
 * to the Gateway (D-SEC-4).
 *
 * <p>PII contract: this class never logs patient names, addresses, phones, or dates of birth.
 * Only counts and IDs are permitted in log statements (Logging discipline — epics.md §108).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PatientGatewayClient {

    private final RestClient gatewayClient;

    /**
     * Retrieves all patients from the Gateway ({@code GET /patients}).
     *
     * @return the list of patients; empty list if none exist
     */
    public List<PatientView> getAllPatients() {
        List<PatientView> patients = gatewayClient.get()
                .uri("/patients")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        log.debug("Fetched {} patient(s) from Gateway", patients != null ? patients.size() : 0);
        return patients != null ? patients : List.of();
    }
}
