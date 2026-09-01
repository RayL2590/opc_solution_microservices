package com.medilabo.assessmentservice.config;

import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

/**
 * Authentifie chaque appel sortant vers le Gateway avec le compte machine d'assessment-service.
 *
 * <p>Le credential du clinicien n'est jamais relayé entre services : chaque service a sa propre identité, qu'on peut révoquer indépendamment des autres. Pas de dépendance au contexte de requête ici, donc ça marche aussi bien depuis un thread {@code @Async} ou au démarrage.
 */
public class ServiceAccountAuthInitializer implements ClientHttpRequestInitializer {

    private final String username;
    /** Volontairement en clair (pas le hash BCrypt) : le header Basic Auth sortant a besoin du mot de passe réel. */
    private final String password;

    public ServiceAccountAuthInitializer(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void initialize(ClientHttpRequest request) {
        request.getHeaders().setBasicAuth(username, password);
    }
}
