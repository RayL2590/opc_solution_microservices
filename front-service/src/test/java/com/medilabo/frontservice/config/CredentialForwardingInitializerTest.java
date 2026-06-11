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
 * Pure-unit oracle for {@link CredentialForwardingInitializer} (Story 5.1, D-SEC-4).
 *
 * <p>No Spring context, no network. The inbound request is faked by binding a
 * {@link MockHttpServletRequest} to the current thread via {@link RequestContextHolder}; the
 * outbound request is a {@link MockClientHttpRequest} whose headers are inspected after the
 * initializer runs. {@link RequestContextHolder} is reset after each test to avoid bleed.
 *
 * <p>Oracles:
 * <ul>
 *   <li>Inbound {@code Authorization} present → the same value is set on the outbound request.</li>
 *   <li>No bound request context → nothing is set, no exception (background/boot threads).</li>
 *   <li>Bound request but no {@code Authorization} header → nothing is set.</li>
 * </ul>
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
