package com.medilabo.gatewayservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Reactive security slice for the Gateway (Story 1.4). Boots the full WebFlux
 * context ({@code RANDOM_PORT}) so the real {@link org.springframework.security.web.server.SecurityWebFilterChain}
 * is exercised end-to-end.
 *
 * <p>Oracles:
 * <ul>
 *   <li>AC-1 — no credentials ⇒ HTTP 401 on any path (routed and unrouted).</li>
 *   <li>AC-3 — the seeded user's stored password is a BCrypt hash (starts with {@code $2}),
 *       and the {@link PasswordEncoder} bean is a {@link BCryptPasswordEncoder}.</li>
 *   <li>AC-2 (documented fallback) — valid credentials pass the credential check
 *       (status is NOT 401); full authenticated 200 + header pass-through is deferred to
 *       Story 6.2 (compose, real upstream). Host port 8081 here is the external httpd,
 *       so the forward yields a non-401 upstream/connection status, which still proves
 *       the credential check succeeded. No {@code Authorization}-removing route filter exists.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SecurityConfigTest {

    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";

    @LocalServerPort
    private int port;

    @Autowired
    private MapReactiveUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void bindClient() {
        // Bind to the running random-port server. @AutoConfigureWebTestClient is not on
        // the classpath in Boot 4 (its module isn't pulled in), so we build the client
        // manually. Generous timeout: the localhost:8081 upstream is the external httpd /
        // unreachable, so authenticated forwards yield a slow non-401/5xx response.
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }

    // --- AC-1: unauthenticated request -> 401 -------------------------------

    @Test
    void unauthenticatedRoutedRequestIsUnauthorized() {
        webTestClient.get().uri("/patients/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedUnroutedRequestIsUnauthorized() {
        // anyExchange().authenticated() must protect paths with no matching route too.
        webTestClient.get().uri("/no-such-path")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // --- AC-3: seeded store is a BCrypt hash, encoder is BCrypt -------------

    @Test
    void seededUserPasswordIsStoredAsBcryptHash() {
        UserDetails user = userDetailsService.findByUsername(DEMO_USER).block();
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(DEMO_USER);
        // Stored verbatim as a BCrypt hash (never plaintext, never double-encoded).
        assertThat(user.getPassword()).startsWith("$2");
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    // --- AC-2 (documented fallback): valid creds pass the credential check --

    @Test
    void validCredentialsPassAuthenticationCheck() {
        // The upstream localhost:8081 is unreachable as patient-service (external httpd /
        // connection error), so the gateway yields a non-401 upstream or 5xx status —
        // never a 401. A 401 here would mean the credential check failed.
        webTestClient.get().uri("/patients/1")
                .headers(h -> h.setBasicAuth(DEMO_USER, DEMO_RAW_PASSWORD))
                .exchange()
                .expectStatus().value(status ->
                        assertThat(status)
                                .as("valid credentials must clear the 401 auth gate")
                                .isNotEqualTo(401));
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        // Guards against a NoOp / misconfigured encoder that would accept any password
        // (which would still satisfy validCredentialsPassAuthenticationCheck).
        webTestClient.get().uri("/patients/1")
                .headers(h -> h.setBasicAuth(DEMO_USER, "definitely-the-wrong-password"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unknownUserIsUnauthorized() {
        webTestClient.get().uri("/patients/1")
                .headers(h -> h.setBasicAuth("not-a-real-user", DEMO_RAW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void noRouteStripsTheAuthorizationHeader() {
        // Pass-through (D-SEC-3): the four routes carry no gateway filters, so the inbound
        // Authorization header is forwarded unchanged (nothing removes/rewrites it).
        for (Route route : routeLocator.getRoutes().collectList().block()) {
            assertThat(route.getFilters())
                    .as("route %s must declare no gateway filter (no Authorization rewrite/strip)", route.getId())
                    .isEmpty();
        }
    }

    @Test
    void noGatewayDefaultFiltersConfigured() {
        // Closes the D-SEC-3 regression gap: per-route .getFilters() emptiness is not enough,
        // because spring.cloud.gateway.server.webflux.default-filters would apply to EVERY
        // route without showing up in route.getFilters() at this level. Assert the property
        // is absent (or empty) so a future RemoveRequestHeader=Authorization default-filter
        // cannot silently strip the header while the per-route assertion stays green.
        String defaultFilters = environment.getProperty(
                "spring.cloud.gateway.server.webflux.default-filters");
        assertThat(defaultFilters)
                .as("spring.cloud.gateway.server.webflux.default-filters must be absent (no global filter chain)")
                .isNullOrEmpty();
    }

    @Test
    void noCustomAuthorizationGlobalFilterBean() {
        // The Gateway ships built-in GlobalFilter beans (NettyRoutingFilter, ForwardRoutingFilter,
        // RouteToRequestUrlFilter, etc.) — those are required infrastructure. Guard only against
        // a user-defined GlobalFilter whose bean name suggests Authorization rewriting/stripping,
        // which would silently break pass-through (D-SEC-3) with all other tests still green.
        Map<String, GlobalFilter> globalFilters = applicationContext.getBeansOfType(GlobalFilter.class);
        assertThat(globalFilters.keySet())
                .as("no GlobalFilter bean may be named after Authorization handling (would break pass-through)")
                .noneMatch(name -> name.toLowerCase().contains("authorization")
                        || name.toLowerCase().contains("authheader"));
    }
}
