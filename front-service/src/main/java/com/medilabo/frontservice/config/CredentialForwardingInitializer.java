package com.medilabo.frontservice.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.extern.slf4j.Slf4j;

/**
 * Re-attaches the inbound {@code Authorization} header to every outbound {@code gatewayClient}
 * call (Story 5.1, D-SEC-4 — credential forwarding, "call the back-end <em>as me</em>").
 *
 * <p>The Gateway is pass-through (Story 1.4): it forwards {@code Authorization} unchanged. The
 * front mirrors that on the <em>outbound</em> side. Because the front uses HTTP Basic
 * ({@code SecurityConfig}), the browser re-sends the {@code Authorization: Basic …} header on
 * every request; this initializer reads it from the servlet request bound to the current
 * thread ({@link RequestContextHolder}) and sets the same value on each outbound request, so
 * the Gateway authenticates the call as the same {@code medilabo} identity end-to-end.
 *
 * <p>Both edges are guarded:
 * <ul>
 *   <li>No bound request context (a non-request thread — context boot, a future scheduled
 *       task) → forward nothing, no exception.</li>
 *   <li>Bound request but no/blank {@code Authorization} header → forward nothing.</li>
 * </ul>
 *
 * <p>The header <strong>value is never logged</strong> (NFR — no credential in logs): at most a
 * boolean "forwarded?" at debug level.
 */
@Slf4j
public class CredentialForwardingInitializer implements ClientHttpRequestInitializer {

    @Override
    public void initialize(ClientHttpRequest request) {
        String authorization = inboundAuthorizationHeader();
        if (StringUtils.hasText(authorization)) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            log.debug("Forwarded inbound Authorization to outbound gateway call");
        } else {
            log.debug("No inbound Authorization to forward");
        }
    }

    /**
     * Reads the {@code Authorization} header of the servlet request bound to the current
     * thread, or {@code null} when there is no active request context.
     *
     * @return the inbound {@code Authorization} header value, or {@code null} if absent
     */
    private String inboundAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
