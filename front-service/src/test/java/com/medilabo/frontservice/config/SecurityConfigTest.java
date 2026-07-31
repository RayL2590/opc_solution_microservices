package com.medilabo.frontservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** SecurityConfig instancié directement, sans contexte Spring. */
class SecurityConfigTest {

    // Identifiants démo DEV. Compte du clinicien :
    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String DEMO_BCRYPT_HASH =
            "$2a$10$GzMGhp/NWTujVhv4VyYh9eM.aia95IXMsse7Yl6jUC3DC42/VIinq";

    /** Comptes machine — jamais semés ici : front-service n'a aucun appelant machine entrant. */
    private static final String SVC_FRONT_USER = "svc-front";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";

    private final SecurityConfig securityConfig = new SecurityConfig();

    private UserDetailsService userDetailsService() {
        return securityConfig.userDetailsService(DEMO_USER, DEMO_BCRYPT_HASH);
    }

    @Test
    void seededUserPasswordIsStoredAsBcryptHashNotPlaintext() {
        UserDetails user = userDetailsService().loadUserByUsername(DEMO_USER);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(DEMO_USER);
        // Stocké tel quel comme hash BCrypt — jamais en clair, jamais ré-encodé.
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(user.getPassword()).isEqualTo(DEMO_BCRYPT_HASH);
        assertThat(user.getPassword()).isNotEqualTo(DEMO_RAW_PASSWORD);
    }

    @Test
    void storedHashMatchesRawDemoPasswordButNotTheOldScaffoldPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetails user = userDetailsService().loadUserByUsername(DEMO_USER);

        // Aucun double-hash ; ancien password scaffolding rejeté.
        assertThat(encoder.matches(DEMO_RAW_PASSWORD, user.getPassword())).isTrue();
        assertThat(encoder.matches("user123", user.getPassword())).isFalse();
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(securityConfig.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }

    /**
     * front-service n'a aucun appelant machine entrant : semer un compte de service ici
     * reviendrait à accepter un mot de passe inter-services comme login UI, et donc à
     * exposer les données patients à quiconque détient ce credential.
     */
    @Test
    void serviceAccountsAreNotAcceptedAsUiLogins() {
        UserDetailsService uds = userDetailsService();

        assertThatThrownBy(() -> uds.loadUserByUsername(SVC_FRONT_USER))
                .isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> uds.loadUserByUsername(SVC_ASSESSMENT_USER))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void theClinicianIsTheOnlySeededIdentity() {
        UserDetailsService uds = userDetailsService();

        assertThat(uds.loadUserByUsername(DEMO_USER)).isNotNull();
        assertThat(uds.loadUserByUsername(DEMO_USER).getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_USER");
    }
}
