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

/**
 * Reactive (WebFlux) HTTP Basic security for the Gateway.
 *
 * <p>The Gateway runs on Spring WebFlux / reactive Netty, so this MUST use the
 * reactive Spring Security API ({@code @EnableWebFluxSecurity} +
 * {@link ServerHttpSecurity} + {@link MapReactiveUserDetailsService}) — NOT the
 * servlet API used by the back-end services. Every request to a routed path is
 * authenticated; the inbound {@code Authorization} header is forwarded unchanged
 * to the upstream (pass-through, D-SEC-3). CSRF is disabled and the chain is
 * stateless (no session, NFR-S4/S5).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** BCrypt encoder used to match the raw login password against the stored hash. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Single in-memory user seeded from configuration. The password value is an
     * <em>already-hashed</em> BCrypt string ({@code MEDILABO_PASSWORD_BCRYPT}) and is
     * stored verbatim — it must never be re-encoded (that would double-hash and break
     * login) nor prefixed with {@code {bcrypt}}.
     *
     * @param username the login name (env {@code MEDILABO_USER}, local default {@code medilabo})
     * @param bcryptHash a 60-char BCrypt hash (env {@code MEDILABO_PASSWORD_BCRYPT})
     * @return a reactive user-details service holding the single seeded user
     */
    @Bean
    MapReactiveUserDetailsService userDetailsService(
            @Value("${medilabo.user}") String username,
            @Value("${medilabo.password-bcrypt}") String bcryptHash) {
        UserDetails user = User.withUsername(username)
                .password(bcryptHash) // already a BCrypt hash — stored as-is, no re-encode
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(user);
    }

    /**
     * Reactive security filter chain: HTTP Basic on every exchange, CSRF off,
     * stateless (no security context persisted). Replaces Boot's auto-generated
     * default password configuration.
     *
     * @param http the reactive HTTP security builder provided by Spring Security
     * @return the configured {@link SecurityWebFilterChain}
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange.anyExchange().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
