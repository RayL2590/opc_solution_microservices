package com.medilabo.frontservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Vérification unitaire du CredentialForwardingInitializer (sans contexte Spring).
 * RequestContextHolder réinitialisé après chaque test pour éviter la contamination.
 */
class CredentialForwardingInitializerTest {

    private final CredentialForwardingInitializer initializer = new CredentialForwardingInitializer();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsInboundAuthorizationToOutboundRequest() {
        String inboundAuthorization = "Basic bWVkaWxhYm86bWVkaWxhYm8xMjM=";
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(HttpHeaders.AUTHORIZATION, inboundAuthorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest();
        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo(inboundAuthorization);
    }

    @Test
    void forwardsNothingWhenNoRequestContextBound() {
        // No RequestContextHolder attributes (a background / boot thread).
        MockClientHttpRequest outbound = new MockClientHttpRequest();

        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void forwardsNothingWhenInboundHasNoAuthorizationHeader() {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest();
        initializer.initialize(outbound);

        assertThat(outbound.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
