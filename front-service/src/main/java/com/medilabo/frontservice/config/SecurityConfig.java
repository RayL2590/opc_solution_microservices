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
 * Servlet (Spring MVC) HTTP Basic security for front-service (Story 5.1, FR-15/FR-16,
 * NFR-S2/NFR-S3).
 *
 * <p>Replicates the patient-service contract (Story 2.4) and, through it, the Gateway's
 * contract (Story 1.4): an {@link InMemoryUserDetailsManager} seeded from the
 * {@code MEDILABO_USER} / {@code MEDILABO_PASSWORD_BCRYPT} environment variables (via the
 * {@code medilabo.user} / {@code medilabo.password-bcrypt} bridge in
 * {@code application.properties}), HTTP Basic on every request, CSRF disabled (no HTML form
 * posts in this story), and a stateless chain ({@link SessionCreationPolicy#STATELESS}).
 *
 * <p>HTTP Basic — not form login — is deliberate (Story 5.1 AC: "unauthenticated access
 * yields the Basic challenge"). Basic keeps the chain stateless (no {@code JSESSIONID}) and
 * makes credential forwarding trivial: the browser re-sends the {@code Authorization} header
 * on every request, so the outbound {@code gatewayClient} always has a header to re-attach
 * (D-SEC-4 — see {@link CredentialForwardingInitializer}). The authenticated principal is
 * still surfaced in the layout via {@code sec:authentication}.
 *
 * <p>The password value ({@code MEDILABO_PASSWORD_BCRYPT}, via the
 * {@code medilabo.password-bcrypt} placeholder) is an <em>already-hashed</em> BCrypt string
 * and is stored <strong>verbatim</strong> — it must never be re-encoded
 * ({@code encoder.encode(hash)} would double-hash and silently break every login while the
 * context still boots green) nor prefixed with {@code {bcrypt}}. The
 * {@link BCryptPasswordEncoder} bean is consulted by Spring Security only to match the raw
 * inbound login password against this stored hash. No plaintext credential lives in any
 * tracked file (NFR-S3): the bridge placeholders are
 * {@code ${MEDILABO_USER:…}} / {@code ${MEDILABO_PASSWORD_BCRYPT:…}}, so an env override
 * actually takes effect and the committed DEV/eval default is the demo identity
 * {@code medilabo} + the BCrypt hash of {@code medilabo123} (the same identity the Gateway
 * and patient-service commit, so the forwarded credential authenticates end-to-end).
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
     * (no session persisted). Replaces Boot's auto-generated default login. An
     * unauthenticated request receives the HTTP Basic challenge
     * ({@code 401 WWW-Authenticate: Basic}).
     *
     * @param http the servlet HTTP security builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // no HTML form posts in this story
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {}); // HTTP Basic challenge for unauthenticated access
        return http.build();
    }
}
