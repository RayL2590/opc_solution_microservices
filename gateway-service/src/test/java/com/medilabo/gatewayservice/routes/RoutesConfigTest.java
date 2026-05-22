package com.medilabo.gatewayservice.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * AC-2 oracle: proves the Gateway loads exactly the four configured routes
 * ({@code patients}, {@code notes}, {@code assessments}, {@code front}) and that
 * each route's path predicate matches a representative request path.
 *
 * <p>Boots the full reactive context so the YAML route definitions under
 * {@code spring.cloud.gateway.server.webflux.routes} are resolved by the real
 * {@link RouteLocator} — this is what would catch a wrong property prefix
 * (the routes list would be empty).
 */
@SpringBootTest
class RoutesConfigTest {

	@Autowired
	private RouteLocator routeLocator;

	private Map<String, Route> routesById() {
		List<Route> routes = routeLocator.getRoutes().collectList().block();
		assertThat(routes).isNotNull();
		return routes.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
	}

	private boolean matches(Route route, String path) {
		ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path));
		Boolean result = Mono.from(route.getPredicate().apply(exchange)).block();
		return Boolean.TRUE.equals(result);
	}

	@Test
	void loadsExactlyTheFourConfiguredRoutes() {
		assertThat(routesById().keySet())
				.containsExactlyInAnyOrder("patients", "notes", "assessments", "front");
	}

	@Test
	void eachRouteMatchesItsRepresentativePath() {
		Map<String, Route> routes = routesById();

		assertThat(matches(routes.get("patients"), "/patients/x")).isTrue();
		assertThat(matches(routes.get("notes"), "/notes")).isTrue();
		assertThat(matches(routes.get("assessments"), "/assessments/1")).isTrue();
		// G-1: the single `front` route carries both `/` and `/ui/**` (including bare `/ui`).
		assertThat(matches(routes.get("front"), "/")).isTrue();
		assertThat(matches(routes.get("front"), "/ui")).isTrue();
		assertThat(matches(routes.get("front"), "/ui/anything")).isTrue();
	}

	@Test
	void routesDoNotLeakAcrossPaths() {
		Map<String, Route> routes = routesById();

		// front must not swallow the API routes — guards against an over-greedy
		// predicate (e.g. Path=/**) silently capturing /patients/**.
		assertThat(matches(routes.get("front"), "/patients/x")).isFalse();
		assertThat(matches(routes.get("front"), "/notes")).isFalse();
		// API routes stay disjoint from one another.
		assertThat(matches(routes.get("patients"), "/notes")).isFalse();
		assertThat(matches(routes.get("notes"), "/assessments/1")).isFalse();
	}

	@Test
	void routesUseDirectHostPortUrisWithoutLoadBalancing() {
		routesById().values().forEach(route ->
				assertThat(route.getUri().getScheme())
						.as("route %s must use a direct http URI, not lb://", route.getId())
						.isEqualTo("http"));
	}

	@Test
	void defaultProfileResolvesEachRouteToItsLocalhostTarget() {
		Map<String, Route> routes = routesById();

		// Backs the AC-3 fallback: the /patients/** route resolves to
		// http://localhost:8081 even though full authenticated E2E against a
		// running patient-service is deferred (host port 8081 is occupied).
		assertThat(routes.get("patients").getUri()).hasToString("http://localhost:8081");
		assertThat(routes.get("notes").getUri()).hasToString("http://localhost:8082");
		assertThat(routes.get("assessments").getUri()).hasToString("http://localhost:8083");
		assertThat(routes.get("front").getUri()).hasToString("http://localhost:8084");
	}
}
