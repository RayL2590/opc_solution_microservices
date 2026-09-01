package com.medilabo.frontservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Seul test du module à démarrer le contexte Spring complet. Tous les autres sont des tranches {@code @WebMvcTest} ou des tests unitaires purs qui ne touchent ni à {@code RestClientConfig}, ni aux {@code @Value} de {@code application.properties}, ni à l'import du {@code .env}. Corps de test vide volontaire : le démarrage lui-même est l'assertion (un bean mal câblé ou un placeholder non résolu fait planter {@code contextLoads}, et rien d'autre ne le détecterait).
 */
@SpringBootTest
class FrontServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
