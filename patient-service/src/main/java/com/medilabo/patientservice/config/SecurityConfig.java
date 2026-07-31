package com.medilabo.patientservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Trois identités distinctes : le clinicien (ROLE_USER) et un compte machine par service
     * appelant. Séparer les comptes évite de faire circuler le mot de passe du clinicien entre
     * les services, et permet de révoquer un appelant sans toucher aux autres.
     * Les hashes sont stockés tels quels — jamais de ré-encodage.
     *
     * <p>Chaque compte machine porte ROLE_SERVICE (le marqueur commun) plus un rôle qui lui est
     * propre, sinon impossible de distinguer qui a le droit de lire et qui a le droit d'écrire.
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${medilabo.user}") String username,
            @Value("${medilabo.password-bcrypt}") String bcryptHash,
            @Value("${medilabo.svc-front-user}") String svcFrontUsername,
            @Value("${medilabo.svc-front-password-bcrypt}") String svcFrontBcryptHash,
            @Value("${medilabo.svc-assessment-user}") String svcAssessmentUsername,
            @Value("${medilabo.svc-assessment-password-bcrypt}") String svcAssessmentBcryptHash) {
        UserDetails user = User.withUsername(username)
                .password(bcryptHash) // déjà un hash BCrypt, on le stocke tel quel, pas de ré-encodage
                .roles("USER")
                .build();
        UserDetails svcFront = User.withUsername(svcFrontUsername)
                .password(svcFrontBcryptHash)
                .roles("SERVICE", "SERVICE_FRONT")
                .build();
        UserDetails svcAssessment = User.withUsername(svcAssessmentUsername)
                .password(svcAssessmentBcryptHash)
                .roles("SERVICE", "SERVICE_ASSESSMENT")
                .build();
        return new InMemoryUserDetailsManager(user, svcFront, svcAssessment);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API REST, pas de formulaire HTML
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Moindre privilège : svc-assessment ne fait que lire un patient pour calculer un
            // risque, il n'écrit jamais. Les écritures restent au clinicien et à svc-front,
            // qui porte ses actions depuis l'UI.
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/patients/**")
                    .hasAnyRole("USER", "SERVICE_FRONT", "SERVICE_ASSESSMENT")
                .requestMatchers("/patients/**").hasAnyRole("USER", "SERVICE_FRONT")
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {});
        return http.build();
    }
}
