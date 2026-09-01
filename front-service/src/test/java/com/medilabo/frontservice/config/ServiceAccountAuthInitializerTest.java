package com.medilabo.frontservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Le credential du clinicien ne doit jamais fuiter dans un appel sortant : c'est toujours le compte machine qui est posé, même si le contexte de requête en contient un autre.
 */
class ServiceAccountAuthInitializerTest {

    private static final String SERVICE_USER = "svc-front";
    private static final String SERVICE_PASSWORD = "svcfront123";

    private final ServiceAccountAuthInitializer initializer =
            new ServiceAccountAuthInitializer(SERVICE_USER, SERVICE_PASSWORD);

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static String basicHeaderFor(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void attachesServiceAccountBasicHeader() {
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(basicHeaderFor(SERVICE_USER, SERVICE_PASSWORD));
    }

    @Test
    void attachesServiceAccountEvenWhenNoRequestContextBound() {
        // Aucun attribut RequestContextHolder (thread @Async / de démarrage).
        // L'ancien CredentialForwardingInitializer perdait silencieusement le header ici.
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(basicHeaderFor(SERVICE_USER, SERVICE_PASSWORD));
    }

    @Test
    void ignoresInboundAuthorizationHeaderAndNeverLeaksTheHumanCredential() {
        String humanAuthorization = basicHeaderFor("medilabo", "medilabo123");
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.AUTHORIZATION, humanAuthorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest();
        initializer.initialize(outbound);

        // Cœur de la décorrélation : le credential du clinicien ne sort jamais du service.
        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(basicHeaderFor(SERVICE_USER, SERVICE_PASSWORD))
                .isNotEqualTo(humanAuthorization);
    }

    @Test
    void overwritesAnyPreexistingAuthorizationHeaderOnTheOutboundRequest() {
        MockClientHttpRequest outbound = new MockClientHttpRequest();
        outbound.getHeaders().set(HttpHeaders.AUTHORIZATION, basicHeaderFor("medilabo", "medilabo123"));

        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().get(HttpHeaders.AUTHORIZATION))
                .containsExactly(basicHeaderFor(SERVICE_USER, SERVICE_PASSWORD));
    }
}
