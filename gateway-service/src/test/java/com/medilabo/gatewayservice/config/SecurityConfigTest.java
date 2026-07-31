package com.medilabo.gatewayservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
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

@SpringBootTest(webEnvironment = RANDOM_PORT)
class SecurityConfigTest {

    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";

    // Comptes machine, un par appelant
    private static final String SVC_FRONT_USER = "svc-front";
    private static final String SVC_FRONT_RAW_PASSWORD = "svcfront123";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";
    private static final String SVC_ASSESSMENT_RAW_PASSWORD = "svcassess123";

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
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }


    @Test
    void unauthenticatedRoutedRequestIsUnauthorized() {
        webTestClient.get().uri("/patients/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedUnroutedRequestIsUnauthorized() {
        webTestClient.get().uri("/no-such-path")
                .exchange()
                .expectStatus().isUnauthorized();
    }


    @Test
    void seededUserPasswordIsStoredAsBcryptHash() {
        UserDetails user = userDetailsService.findByUsername(DEMO_USER).block();
        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(DEMO_USER);
        assertThat(user.getPassword()).startsWith("$2");
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void validCredentialsPassAuthenticationCheck() {
        // 8081 est pris par un httpd externe en local, du coup on vérifie juste qu'on passe le 401, pas que le service répond vraiment.
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
    void serviceAccountsAreSeededWithBcryptHashesAndTheServiceRole() {
        for (String svcUser : List.of(SVC_FRONT_USER, SVC_ASSESSMENT_USER)) {
            UserDetails user = userDetailsService.findByUsername(svcUser).block();
            assertThat(user).as("service account %s must be seeded", svcUser).isNotNull();
            assertThat(user.getPassword()).startsWith("$2");
            assertThat(user.getAuthorities()).extracting(Object::toString)
                    .contains("ROLE_SERVICE");
        }
    }

    @Test
    void theThreeSeededIdentitiesArePairwiseDistinct() {
        List<String> hashes = List.of(DEMO_USER, SVC_FRONT_USER, SVC_ASSESSMENT_USER).stream()
                .map(name -> userDetailsService.findByUsername(name).block().getPassword())
                .toList();

        // trois credentials vraiment différents, pas un alias partagé
        assertThat(hashes).doesNotHaveDuplicates();
    }

    @Test
    void eachServiceAccountPassesAuthenticationWithItsOwnPassword() {
        // 8081 est pris par un httpd externe en local, du coup on vérifie juste qu'on passe le 401.
        Map<String, String> serviceCredentials = Map.of(
                SVC_FRONT_USER, SVC_FRONT_RAW_PASSWORD,
                SVC_ASSESSMENT_USER, SVC_ASSESSMENT_RAW_PASSWORD);

        serviceCredentials.forEach((user, password) ->
                webTestClient.get().uri("/patients/1")
                        .headers(h -> h.setBasicAuth(user, password))
                        .exchange()
                        .expectStatus().value(status ->
                                assertThat(status)
                                        .as("service account %s must clear the 401 auth gate", user)
                                        .isNotEqualTo(401)));
    }

    @Test
    void serviceAccountsDoNotAcceptEachOthersPassword() {
        // Si front-service est compromis, ça ne doit pas donner l'identité d'assessment-service.
        webTestClient.get().uri("/patients/1")
                .headers(h -> h.setBasicAuth(SVC_FRONT_USER, SVC_ASSESSMENT_RAW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();

        webTestClient.get().uri("/patients/1")
                .headers(h -> h.setBasicAuth(SVC_ASSESSMENT_USER, DEMO_RAW_PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void noRouteStripsTheAuthorizationHeader() {
        for (Route route : routeLocator.getRoutes().collectList().block()) {
            // Les autres filtres sont permis (AddRequestHeader X-Forwarded-* sur la route front),
            // mais Authorization doit traverser intact. Sinon les backends ne savent plus
            // qui est derrière la requête.
            assertThat(route.getFilters())
                    .as("route %s must not rewrite/strip the Authorization header", route.getId())
                    .noneMatch(filter -> filter.toString().toLowerCase().contains("authorization"));
        }
    }

    @Test
    void noGatewayDefaultFiltersConfigured() {
        String defaultFilters = environment.getProperty(
                "spring.cloud.gateway.server.webflux.default-filters");
        assertThat(defaultFilters)
                .as("spring.cloud.gateway.server.webflux.default-filters must be absent (no global filter chain)")
                .isNullOrEmpty();
    }

    @Test
    void noCustomAuthorizationGlobalFilterBean() {
        Map<String, GlobalFilter> globalFilters = applicationContext.getBeansOfType(GlobalFilter.class);
        assertThat(globalFilters.keySet())
                .as("no GlobalFilter bean may be named after Authorization handling (would break pass-through)")
                .noneMatch(name -> name.toLowerCase().contains("authorization")
                        || name.toLowerCase().contains("authheader"));
    }
}
