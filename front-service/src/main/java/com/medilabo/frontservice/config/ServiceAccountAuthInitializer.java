package com.medilabo.frontservice.config;

import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestInitializer;

/**
 * Pose le Basic Auth du compte machine de front-service sur chaque appel vers le Gateway.
 *
 * <p>Le mot de passe saisi par le clinicien reste dans la session web, il n'est jamais
 * renvoyé au Gateway. Ne dépend pas du contexte de requête : marche aussi hors thread HTTP.
 */
public class ServiceAccountAuthInitializer implements ClientHttpRequestInitializer {

    private final String username;
    /** En clair : le header Basic se construit avec le mot de passe, le hash n'est utile qu'au vérificateur. */
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
