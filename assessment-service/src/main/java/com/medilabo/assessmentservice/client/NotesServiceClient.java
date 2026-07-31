package com.medilabo.assessmentservice.client;

import com.medilabo.assessmentservice.dto.NoteView;
import com.medilabo.assessmentservice.exception.BadGatewayException;
import com.medilabo.assessmentservice.exception.GatewayTimeoutException;
import com.medilabo.assessmentservice.exception.UpstreamNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client upstream pour les notes cliniques (Gateway → notes-service).
 * L'authentification sortante passe par ServiceAccountAuthInitializer (compte de service).
 * PII : seuls patientId et le nombre de notes finissent dans les logs, jamais le texte des notes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotesServiceClient {

    private final RestClient gatewayClient;

    /**
     * Récupère les notes d'un patient, triées most-recent-first par notes-service.
     *
     * @return liste de {@link NoteView}, jamais null (vide si aucune note)
     * @throws UpstreamNotFoundException notes-service a renvoyé 404
     * @throws BadGatewayException       notes-service a renvoyé 5xx
     * @throws GatewayTimeoutException   connexion refusée ou expirée
     */
    public List<NoteView> getNotesByPatId(Integer patientId) {
        log.debug("Fetching notes for patientId={}", patientId);
        try {
            List<NoteView> notes = gatewayClient.get()
                    .uri("/notes?patId={patId}", patientId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            List<NoteView> result = notes != null ? notes : List.of();
            log.debug("Fetched {} note(s) for patientId={}", result.size(), patientId);
            return result;

        } catch (HttpClientErrorException.NotFound e) {
            throw new UpstreamNotFoundException("Notes introuvables : patientId=" + patientId, e);
        } catch (HttpServerErrorException e) {
            throw new BadGatewayException(
                    "Erreur upstream notes-service pour patientId=" + patientId, e);
        } catch (ResourceAccessException e) {
            throw new GatewayTimeoutException(
                    "notes-service inaccessible pour patientId=" + patientId, e);
        }
    }
}
