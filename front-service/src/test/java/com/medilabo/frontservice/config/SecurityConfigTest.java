package com.medilabo.frontservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Vérification unitaire des beans de sécurité front-service (SecurityConfig instancié directement).
 * Miroir de patient-service SecurityConfigTest.
 */
class SecurityConfigTest {

    /** Identifiants démo DEV partagés avec Gateway et patient-service. */
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
        assertThat(user.getPassword()).isNotEqualTo(DEMO_RAW_PASSWORD);
    }

    @Test
    void storedHashMatchesRawDemoPasswordButNotTheOldScaffoldPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetails user =
                securityConfig.userDetailsService(DEMO_USER, DEMO_BCRYPT_HASH)
                        .loadUserByUsername(DEMO_USER);

        // Aucun double-hash ; ancien password scaffolding rejeté.
        assertThat(encoder.matches(DEMO_RAW_PASSWORD, user.getPassword())).isTrue();
        assertThat(encoder.matches("user123", user.getPassword())).isFalse();
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(securityConfig.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }
}
