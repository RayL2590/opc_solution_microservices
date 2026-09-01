package com.medilabo.frontservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité servlet de front-service — même contrat que patient-service (HTTP Basic, CSRF off, STATELESS).
 * HTTP Basic délibéré : stateless, le navigateur ré-envoie Authorization à chaque requête.
 * Ce credential authentifie le clinicien ici et s'arrête là ; les appels sortants vers le Gateway passent par {@link ServiceAccountAuthInitializer}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Une seule identité ici : le clinicien (ROLE_USER). front-service n'a aucun appelant machine entrant, c'est le point d'entrée humain de la chaîne. Semer les comptes de service ici reviendrait à accepter un mot de passe machine comme login UI : n'importe qui avec un credential inter-services pourrait alors voir les données patients. Le hash est stocké tel quel, jamais de ré-encodage.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${medilabo.user}") String username,
            @Value("${medilabo.password-bcrypt}") String bcryptHash) {
        UserDetails user = User.withUsername(username)
                .password(bcryptHash) // déjà un hash BCrypt, stocké tel quel, pas de ré-encodage
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {});
        return http.build();
    }
}
