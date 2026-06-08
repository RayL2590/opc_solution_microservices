package com.medilabo.patientservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pure-unit oracle for the patient-service security beans (Story 2.4, FR-16 / NFR-S2).
 *
 * <p>No Spring context, no {@code @WebMvcTest}, no DataSource — instantiates
 * {@link SecurityConfig} directly and calls its {@code @Bean} methods. The 401/2xx
 * filter-chain wiring is already exercised by the {@code @WebMvcTest} slice
 * ({@code PatientControllerTest}); this test only inspects the seeded user store, so it
 * deliberately stays off the live-MySQL dependency that {@code @SpringBootTest contextLoads}
 * and {@code PatientRepositoryTest} carry.
 *
 * <p>Oracles:
 * <ul>
 *   <li>The seeded password is stored as a BCrypt hash ({@code $2…}), never plaintext.</li>
 *   <li>The hash is stored <em>verbatim</em> — the raw demo password matches it, proving it
 *       was not re-encoded ({@code encoder.encode(hash)} would double-hash and break login),
 *       and the old scaffold password ({@code user123}) does not match.</li>
 *   <li>The {@link PasswordEncoder} bean is a {@link BCryptPasswordEncoder} (not a NoOp).</li>
 * </ul>
 */
class SecurityConfigTest {

    /** Demo credential (DEV/eval only) — the same identity the Gateway commits (Story 1.4). */
    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String DEMO_BCRYPT_HASH =
            "$2a$10$GzMGhp/NWTujVhv4VyYh9eM.aia95IXMsse7Yl6jUC3DC42/VIinq";

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void seededUserPasswordIsStoredAsBcryptHashNotPlaintext() {
        UserDetailsService uds = securityConfig.userDetailsService(DEMO_USER, DEMO_BCRYPT_HASH);

        UserDetails user = uds.loadUserByUsername(DEMO_USER);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(DEMO_USER);
        // Stored verbatim as a BCrypt hash — never plaintext, never double-encoded.
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(user.getPassword()).isEqualTo(DEMO_BCRYPT_HASH);
        // The plaintext raw password must NOT be what is stored (NFR-S3 / NFR-S2).
        assertThat(user.getPassword()).isNotEqualTo(DEMO_RAW_PASSWORD);
    }

    @Test
    void storedHashMatchesRawDemoPasswordButNotTheOldScaffoldPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetails user =
                securityConfig.userDetailsService(DEMO_USER, DEMO_BCRYPT_HASH)
                        .loadUserByUsername(DEMO_USER);

        // Verbatim storage proof: the raw demo password matches the stored hash (no double-hash),
        // and the retired scaffold password does not.
        assertThat(encoder.matches(DEMO_RAW_PASSWORD, user.getPassword())).isTrue();
        assertThat(encoder.matches("user123", user.getPassword())).isFalse();
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(securityConfig.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }
}
