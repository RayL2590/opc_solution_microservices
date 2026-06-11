package com.medilabo.frontservice.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Outbound HTTP client to the Gateway (Story 5.1, FR-15/FR-16, D-SEC-4).
 *
 * <p>Declares the {@code gatewayClient} {@link RestClient} every UI page (5.2+) calls the
 * back-end through. It is configured with the Gateway base URL, explicit connect/read
 * timeouts, and the {@link CredentialForwardingInitializer} that re-attaches the inbound
 * {@code Authorization} header to each outbound call. The bean makes <strong>no eager network
 * call</strong> at construction — base URL + timeouts + initializer only — so the context boots
 * green with the Gateway down (the front-service is DB-free and, in this story, network-free at
 * boot).
 *
 * <p>The base URL is the local-profile Gateway ({@code http://localhost:8080} — the Gateway's
 * published port, {@code gateway-service/application.yml}). The {@code docker} profile
 * (service-name URI) is Epic 6 / compose work, out of Story 5.1.
 */
@Configuration
public class RestClientConfig {

    /** Connect timeout for outbound Gateway calls (Story 5.1: 5s). */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Read timeout for outbound Gateway calls (Story 5.1: 10s). */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The {@code gatewayClient} used by UI controllers (5.2+) to call the back-end through the
     * Gateway. Configured lazily — no call is made here.
     *
     * @param gatewayBaseUrl the Gateway base URL ({@code medilabo.gateway.base-url}, local
     *                       default {@code http://localhost:8080})
     * @return the configured {@link RestClient}
     */
    @Bean
    public RestClient gatewayClient(
            @Value("${medilabo.gateway.base-url:http://localhost:8080}") String gatewayBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(gatewayBaseUrl)
                .requestFactory(requestFactory)
                .requestInitializer(new CredentialForwardingInitializer())
                .build();
    }
}
