package com.medilabo.assessmentservice.config;

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
 * Le header sortant doit porter le compte de service quoi qu'il arrive : y compris quand une requête entrante authentifiée est présente dans le contexte.
 */
class ServiceAccountAuthInitializerTest {

    private static final String SERVICE_USER = "svc-assessment";
    private static final String SERVICE_PASSWORD = "svcassess123";

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
        // Pas d'attribut RequestContextHolder ici (thread @Async ou de démarrage).
        // L'ancien CredentialForwardingInitializer perdait le header en silence dans ce cas.
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

        // Le point central de la décorrélation : le credential du clinicien ne sort jamais du service.
        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(basicHeaderFor(SERVICE_USER, SERVICE_PASSWORD))
                .isNotEqualTo(humanAuthorization);
    }

    @Test
    void usesAnIdentityDistinctFromFrontServiceAccount() {
        // Question de granularité de révocation : compromettre front-service ne donne pas accès à l'identité d'assessment-service. C'est tout l'intérêt d'avoir un compte par appelant plutôt qu'un compte de service unique partagé entre tous.
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isNotEqualTo(basicHeaderFor("svc-front", "svcfront123"));
    }
}
