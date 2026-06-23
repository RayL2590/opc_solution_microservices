package com.medilabo.notesservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Vérification unitaire des beans de sécurité (SecurityConfig instancié directement, pas de contexte Spring).
 * BCrypt verbatim : le hash match le mot de passe brut — jamais double-encodé.
 */
class SecurityConfigTest {

    /** Identifiants démo DEV partagés avec Gateway, patient-service et front-service. */
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
    void storedHashMatchesRawDemoPasswordButNotAWrongPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetails user =
                securityConfig.userDetailsService(DEMO_USER, DEMO_BCRYPT_HASH)
                        .loadUserByUsername(DEMO_USER);

        assertThat(encoder.matches(DEMO_RAW_PASSWORD, user.getPassword())).isTrue();
        assertThat(encoder.matches("wrong-password", user.getPassword())).isFalse();
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(securityConfig.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }
}
