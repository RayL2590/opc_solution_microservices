package com.medilabo.frontservice.util;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;

/**
 * Extrait les erreurs par champ d'un 400 renvoyé par un service amont, pour les réafficher sur le formulaire plutôt que de laisser remonter une page d'erreur.
 *
 * <p>Les services amont répondent en ProblemDetail (RFC 7807) et déposent leurs erreurs de validation dans une propriété {@code errors}, dont les clés sont les noms de champs :</p>
 *
 * <pre>{@code
 * {"title":"Bad Request","status":400,"detail":"La validation du patient a échoué",
 *  "errors":{"phone":"Le téléphone doit être au format international (ex. +33601020304)"}}
 * }</pre>
 *
 * <p>Utilitaire volontairement tolérant : un corps illisible, absent ou sans {@code errors} renvoie une map vide plutôt que de lever. L'appelant retombe alors sur un message générique — un défaut d'affichage ne doit jamais masquer l'erreur d'origine.</p>
 */
@Slf4j
public final class UpstreamValidationErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UpstreamValidationErrors() {
    }

    /**
     * @param ex l'erreur HTTP remontée par le RestClient
     * @return les couples champ → message, dans l'ordre du corps ; vide si le corps ne suit pas le contrat ProblemDetail attendu
     */
    public static Map<String, String> fieldErrorsOf(RestClientResponseException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return errors;
        }

        try {
            JsonNode node = MAPPER.readTree(body).path("errors");
            if (node.isObject()) {
                node.properties().forEach(entry -> errors.put(entry.getKey(), entry.getValue().stringValue()));
            }
        } catch (Exception parseFailure) {
            // Corps non-JSON ou structure inattendue : on renonce silencieusement aux erreurs par champ, l'appelant affichera le message générique. Loggué en debug seulement, ce n'est pas une anomalie applicative.
            log.debug("Upstream error body is not a parsable ProblemDetail", parseFailure);
        }
        return errors;
    }

    /**
     * Message global à afficher quand aucune erreur par champ n'est exploitable.
     *
     * @return le {@code detail} du ProblemDetail s'il est présent, sinon {@code null} pour laisser l'appelant choisir son propre libellé
     */
    public static String detailOf(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode detail = MAPPER.readTree(body).path("detail");
            return detail.isString() ? detail.stringValue() : null;
        } catch (Exception parseFailure) {
            log.debug("Upstream error body is not a parsable ProblemDetail", parseFailure);
            return null;
        }
    }
}
