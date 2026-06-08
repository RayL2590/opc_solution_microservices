package com.medilabo.patientservice.config;

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
 * Servlet (Spring MVC) HTTP Basic security for patient-service — defense-in-depth
 * (Story 2.4, FR-15/FR-16, NFR-S2/NFR-S3).
 *
 * <p>Replicates the Gateway's security contract (Story 1.4) on the <em>servlet</em>
 * Spring Security API ({@code @EnableWebSecurity} + {@link HttpSecurity} +
 * {@link InMemoryUserDetailsManager}) — NOT the reactive API the Gateway uses. The
 * service is protected even if exposed directly: every request is authenticated
 * (HTTP Basic), CSRF is disabled (REST, no HTML form), and the chain is stateless
 * ({@link SessionCreationPolicy#STATELESS}).
 *
 * <p>The single in-memory user is seeded from configuration. The password value
 * ({@code MEDILABO_PASSWORD_BCRYPT}, via the {@code medilabo.password-bcrypt} placeholder)
 * is an <em>already-hashed</em> BCrypt string and is stored <strong>verbatim</strong> — it
 * must never be re-encoded ({@code encoder.encode(hash)} would double-hash and silently break
 * every login while the context still boots green) nor prefixed with {@code {bcrypt}}. The
 * {@link BCryptPasswordEncoder} bean is consulted by Spring Security only to match the raw
 * inbound login password against this stored hash. No plaintext credential lives in any tracked
 * file (NFR-S3): the {@code medilabo.user} / {@code medilabo.password-bcrypt} placeholders are
 * defined in {@code application.properties} as {@code ${MEDILABO_USER:…}} / {@code ${MEDILABO_PASSWORD_BCRYPT:…}}
 * (mirroring the Gateway's {@code application.yml} bridge — Story 1.4), so an env var override
 * actually takes effect and the committed DEV/eval default is the demo identity {@code medilabo}
 * + the BCrypt hash of {@code medilabo123}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** BCrypt encoder used only to match the raw login password against the stored hash. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Single in-memory user seeded from configuration. The {@code bcryptHash} is stored
     * as-is (verbatim) — never re-encoded, never {@code {bcrypt}}-prefixed.
     *
     * @param username   login name — {@code medilabo.user} (env {@code MEDILABO_USER}, DEV default {@code medilabo})
     * @param bcryptHash a 60-char BCrypt hash — {@code medilabo.password-bcrypt}
     *                   (env {@code MEDILABO_PASSWORD_BCRYPT}, DEV default = committed hash of {@code medilabo123})
     * @return an in-memory user-details service holding the single seeded user
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${medilabo.user}") String username,
            @Value("${medilabo.password-bcrypt}") String bcryptHash) {
        UserDetails user = User.withUsername(username)
                .password(bcryptHash) // already a BCrypt hash — stored as-is, no re-encode
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /**
     * Servlet security filter chain: HTTP Basic on every request, CSRF off, stateless
     * (no session persisted). Replaces Boot's auto-generated default login.
     *
     * @param http the servlet HTTP security builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // REST API, no HTML form
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {}); // HTTP Basic Authentication
        return http.build();
    }
}
