package com.medilabo.frontservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

class UpstreamValidationErrorsTest {

    /** Reproduit un 400 de patient-service : ProblemDetail RFC 7807 avec une map "errors" par champ. */
    private static RestClientResponseException badRequestWith(String body) {
        return new RestClientResponseException(
                "400 Bad Request", HttpStatus.BAD_REQUEST.value(), "Bad Request",
                new HttpHeaders(), body == null ? null : body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Les erreurs par champ du ProblemDetail sont extraites")
    void extracts_field_errors() {
        RestClientResponseException ex = badRequestWith("""
                {"detail":"La validation du patient a échoué","status":400,
                 "errors":{"phone":"Le téléphone doit être au format international (ex. +33601020304)"}}""");

        assertThat(UpstreamValidationErrors.fieldErrorsOf(ex))
                .containsExactly(org.assertj.core.api.Assertions.entry(
                        "phone", "Le téléphone doit être au format international (ex. +33601020304)"));
    }

    @Test
    @DisplayName("Plusieurs champs fautifs : tous remontés")
    void extracts_multiple_field_errors() {
        RestClientResponseException ex = badRequestWith(
                "{\"errors\":{\"firstName\":\"Le prénom est obligatoire\",\"gender\":\"Le genre doit être M ou F\"}}");

        assertThat(UpstreamValidationErrors.fieldErrorsOf(ex))
                .containsOnlyKeys("firstName", "gender");
    }

    @Test
    @DisplayName("Le detail du ProblemDetail sert de message global")
    void extracts_detail() {
        RestClientResponseException ex = badRequestWith(
                "{\"detail\":\"La validation du patient a échoué\",\"status\":400}");

        assertThat(UpstreamValidationErrors.detailOf(ex)).isEqualTo("La validation du patient a échoué");
    }

    @Test
    @DisplayName("Corps illisible, vide ou sans errors : map vide, jamais d'exception")
    void malformed_bodies_yield_empty_map() {
        // Un defaut de parsing ne doit pas masquer l'erreur d'origine : on degrade vers le message generique.
        assertThat(UpstreamValidationErrors.fieldErrorsOf(badRequestWith("pas du json"))).isEmpty();
        assertThat(UpstreamValidationErrors.fieldErrorsOf(badRequestWith(""))).isEmpty();
        assertThat(UpstreamValidationErrors.fieldErrorsOf(badRequestWith("{\"status\":400}"))).isEmpty();
        assertThat(UpstreamValidationErrors.fieldErrorsOf(badRequestWith("{\"errors\":\"pas un objet\"}"))).isEmpty();

        assertThat(UpstreamValidationErrors.detailOf(badRequestWith("pas du json"))).isNull();
        assertThat(UpstreamValidationErrors.detailOf(badRequestWith("{\"status\":400}"))).isNull();
    }
}
