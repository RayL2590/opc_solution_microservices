package com.medilabo.frontservice.client;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medilabo.frontservice.dto.NoteForm;
import com.medilabo.frontservice.dto.NoteView;

/**
 * Client Gateway pour les notes. Credential forwarding géré par CredentialForwardingInitializer.
 * PII : seuls les comptes, ids et compteurs sont loggés — jamais le texte d'une note ni le nom du patient.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotesGatewayClient {

    private final RestClient gatewayClient;

    public List<NoteView> getNotesByPatId(Long patId) {
        List<NoteView> notes = gatewayClient.get()
                .uri("/notes?patId={patId}", patId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        log.debug("Fetched {} note(s) for patId={}", notes != null ? notes.size() : 0, patId);
        return notes != null ? notes : List.of();
    }

    public NoteView addNote(NoteForm form) {
        NoteView created = gatewayClient.post()
                .uri("/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(NoteView.class);
        log.debug("Created note id={} patId={}", created != null ? created.id() : null, form.getPatId());
        return created;
    }
}
