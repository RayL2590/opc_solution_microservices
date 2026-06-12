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
		assertThat(matches(routes.get("front"), "/")).isTrue();
		assertThat(matches(routes.get("front"), "/ui")).isTrue();
		assertThat(matches(routes.get("front"), "/ui/anything")).isTrue();
	}

	@Test
	void routesDoNotLeakAcrossPaths() {
		Map<String, Route> routes = routesById();

		assertThat(matches(routes.get("front"), "/patients/x")).isFalse();
		assertThat(matches(routes.get("front"), "/notes")).isFalse();
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

		assertThat(routes.get("patients").getUri()).hasToString("http://localhost:8081");
		assertThat(routes.get("notes").getUri()).hasToString("http://localhost:8082");
		assertThat(routes.get("assessments").getUri()).hasToString("http://localhost:8083");
		assertThat(routes.get("front").getUri()).hasToString("http://localhost:8084");
	}
}
