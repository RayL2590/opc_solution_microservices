package com.medilabo.patientservice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Pattern;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Détecteur de dérive : épingle la regex réellement portée par {@code PatientDTO.phone}.
 *
 * <p>front-service ne peut pas importer {@link PatientDTO} — modules Maven séparés, pas de module partagé (choix documenté au README). Son test d'invariant {@code PhoneContractInvariantTest} duplique donc cette regex en constante. Cette duplication n'est acceptable que si toute divergence est bruyante : c'est ce que fait ce test.</p>
 *
 * <p>Il ne valide aucun comportement — il constate que la copie de référence n'a pas bougé sans que l'autre côté suive.</p>
 */
class PatientDTOPhonePatternTest {

    // Doit rester identique à PATIENT_DTO_PHONE_REGEX dans front-service/src/test/java/com/medilabo/frontservice/util/PhoneContractInvariantTest.java
    private static final String EXPECTED_PHONE_REGEX = "^(\\+(33|32|41|44|39|1)[0-9]{8,11})?$";

    @Test
    @DisplayName("La regex de PatientDTO.phone n'a pas dérivé de la copie utilisée par le test d'invariant du front")
    void phone_pattern_matches_the_regex_duplicated_in_front_service() throws NoSuchFieldException {
        Field phone = PatientDTO.class.getDeclaredField("phone");
        Pattern pattern = phone.getAnnotation(Pattern.class);

        assertThat(pattern)
                .as("PatientDTO.phone ne porte plus de @Pattern : le garde-fou de format pour les appels API directs a disparu, et PhoneContractInvariantTest (front-service) teste désormais une contrainte qui n'existe plus")
                .isNotNull();

        assertThat(pattern.regexp())
                .as("La regex de PatientDTO.phone a changé. Répercuter la nouvelle valeur dans PATIENT_DTO_PHONE_REGEX, front-service/src/test/java/com/medilabo/frontservice/util/PhoneContractInvariantTest.java, puis relancer ce test d'invariant : il dira si les pays de PhoneCountry passent toujours")
                .isEqualTo(EXPECTED_PHONE_REGEX);
    }
}
