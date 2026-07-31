package com.medilabo.assessmentservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** SecurityConfig instancié directement, sans contexte Spring. */
class SecurityConfigTest {

    // Identifiants démo DEV. Compte humain (le clinicien) :
    private static final String DEMO_USER = "medilabo";
    private static final String DEMO_RAW_PASSWORD = "medilabo123";
    private static final String DEMO_BCRYPT_HASH =
            "$2a$10$GzMGhp/NWTujVhv4VyYh9eM.aia95IXMsse7Yl6jUC3DC42/VIinq";

    // Comptes machine, un par appelant :
    private static final String SVC_FRONT_USER = "svc-front";
    private static final String SVC_FRONT_RAW_PASSWORD = "svcfront123";
    private static final String SVC_FRONT_BCRYPT_HASH =
            "$2a$10$Xd3QlU4YmaRxph5pGkq6guQs7RtdxxF/N3X9xVvyJ.wjiEM6MNwFy";
    private static final String SVC_ASSESSMENT_USER = "svc-assessment";
    private static final String SVC_ASSESSMENT_RAW_PASSWORD = "svcassess123";
    private static final String SVC_ASSESSMENT_BCRYPT_HASH =
            "$2a$10$cQNaASTAabpd03t9EyP6nOCnf.IlVWfa90iFPXZ571UICn69MCjIG";

    private final SecurityConfig securityConfig = new SecurityConfig();

    private UserDetailsService userDetailsService() {
        return securityConfig.userDetailsService(
                DEMO_USER, DEMO_BCRYPT_HASH,
                SVC_FRONT_USER, SVC_FRONT_BCRYPT_HASH,
                SVC_ASSESSMENT_USER, SVC_ASSESSMENT_BCRYPT_HASH);
    }

    @Test
    void seededUserPasswordIsStoredAsBcryptHashNotPlaintext() {
        UserDetails user = userDetailsService().loadUserByUsername(DEMO_USER);

        assertThat(user).isNotNull();
        assertThat(user.getUsername()).isEqualTo(DEMO_USER);
        // stocké tel quel, comme hash BCrypt : jamais en clair, jamais double-encodé
        assertThat(user.getPassword()).startsWith("$2");
        assertThat(user.getPassword()).isEqualTo(DEMO_BCRYPT_HASH);
        assertThat(user.getPassword()).isNotEqualTo(DEMO_RAW_PASSWORD);
    }

    @Test
    void storedHashMatchesRawDemoPasswordButNotAWrongPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetails user = userDetailsService().loadUserByUsername(DEMO_USER);

        assertThat(encoder.matches(DEMO_RAW_PASSWORD, user.getPassword())).isTrue();
        assertThat(encoder.matches("wrong-password", user.getPassword())).isFalse();
    }

    @Test
    void passwordEncoderBeanIsBcrypt() {
        assertThat(securityConfig.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void serviceAccountsAreSeededWithVerbatimBcryptHashesAndTheServiceRole() {
        UserDetailsService uds = userDetailsService();

        UserDetails svcFront = uds.loadUserByUsername(SVC_FRONT_USER);
        assertThat(svcFront.getPassword()).isEqualTo(SVC_FRONT_BCRYPT_HASH);
        assertThat(svcFront.getPassword()).isNotEqualTo(SVC_FRONT_RAW_PASSWORD);
        // ROLE_SERVICE commun, plus un rôle par appelant : svc-front écrit, svc-assessment lit.
        assertThat(svcFront.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_SERVICE_FRONT");

        UserDetails svcAssessment = uds.loadUserByUsername(SVC_ASSESSMENT_USER);
        assertThat(svcAssessment.getPassword()).isEqualTo(SVC_ASSESSMENT_BCRYPT_HASH);
        assertThat(svcAssessment.getPassword()).isNotEqualTo(SVC_ASSESSMENT_RAW_PASSWORD);
        assertThat(svcAssessment.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_SERVICE", "ROLE_SERVICE_ASSESSMENT");
    }

    @Test
    void eachServiceAccountHashMatchesOnlyItsOwnPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        UserDetailsService uds = userDetailsService();

        String svcFrontHash = uds.loadUserByUsername(SVC_FRONT_USER).getPassword();
        String svcAssessmentHash = uds.loadUserByUsername(SVC_ASSESSMENT_USER).getPassword();

        assertThat(encoder.matches(SVC_FRONT_RAW_PASSWORD, svcFrontHash)).isTrue();
        assertThat(encoder.matches(SVC_ASSESSMENT_RAW_PASSWORD, svcAssessmentHash)).isTrue();

        // aucun compte n'accepte le mot de passe des autres
        assertThat(encoder.matches(DEMO_RAW_PASSWORD, svcFrontHash)).isFalse();
        assertThat(encoder.matches(DEMO_RAW_PASSWORD, svcAssessmentHash)).isFalse();
        assertThat(encoder.matches(SVC_ASSESSMENT_RAW_PASSWORD, svcFrontHash)).isFalse();
        assertThat(encoder.matches(SVC_FRONT_RAW_PASSWORD, svcAssessmentHash)).isFalse();
    }

    @Test
    void theThreeSeededIdentitiesArePairwiseDistinct() {
        UserDetailsService uds = userDetailsService();

        assertThat(List.of(DEMO_USER, SVC_FRONT_USER, SVC_ASSESSMENT_USER)).doesNotHaveDuplicates();
        assertThat(List.of(
                uds.loadUserByUsername(DEMO_USER).getPassword(),
                uds.loadUserByUsername(SVC_FRONT_USER).getPassword(),
                uds.loadUserByUsername(SVC_ASSESSMENT_USER).getPassword()))
                .doesNotHaveDuplicates();
    }
}
