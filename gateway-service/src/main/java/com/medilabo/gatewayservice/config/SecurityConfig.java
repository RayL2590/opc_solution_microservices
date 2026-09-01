package com.medilabo.gatewayservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Trois identités : le clinicien (ROLE_USER) et un compte machine par service appelant (ROLE_SERVICE). On les sépare pour que le mot de passe du clinicien ne circule pas entre services, et pour pouvoir révoquer un seul appelant sans toucher aux autres.
     * Le Gateway ne fait qu'accepter ces identités, il relaie juste le header entrant, jamais n'en génère un lui-même.
     */
    @Bean
    MapReactiveUserDetailsService userDetailsService(
            @Value("${medilabo.user}") String username,
            @Value("${medilabo.password-bcrypt}") String bcryptHash,
            @Value("${medilabo.svc-front-user}") String svcFrontUsername,
            @Value("${medilabo.svc-front-password-bcrypt}") String svcFrontBcryptHash,
            @Value("${medilabo.svc-assessment-user}") String svcAssessmentUsername,
            @Value("${medilabo.svc-assessment-password-bcrypt}") String svcAssessmentBcryptHash) {
        UserDetails user = User.withUsername(username)
                .password(bcryptHash)
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
        return new MapReactiveUserDetailsService(user, svcFront, svcAssessment);
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyExchange().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
