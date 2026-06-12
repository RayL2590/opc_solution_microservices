package com.medilabo.frontservice.config;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import lombok.extern.slf4j.Slf4j;

/**
 * Attache le header Authorization entrant à chaque appel sortant vers le Gateway (D-SEC-4).
 * Deux garde-fous : pas de contexte requête → rien ; header absent → rien.
 * Valeur jamais loggée (NFR : pas de credential en logs).
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

    private String inboundAuthorizationHeader() {
        if (RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
