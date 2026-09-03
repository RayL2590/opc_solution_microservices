package com.medilabo.frontservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.medilabo.frontservice.dto.PhoneCountry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Verrouille l'invariant entre les deux services : tout E.164 que {@link PhoneNormalizer} accepte de produire pour un {@link PhoneCountry} doit être accepté par la contrainte {@code phone} de {@code PatientDTO} (patient-service).
 *
 * <p>Sans ce test l'accord entre les deux côtés est une coïncidence : le front impose une longueur nationale exacte par pays, patient-service accepte une fourchette {@code [8,11]} et une liste figée d'indicatifs. Ajouter un pays hors de cette fenêtre, ou dont l'indicatif manque à la regex, produirait un numéro que le front émet et que patient-service rejette en 400.</p>
 *
 * <p>L'itération porte sur {@code PhoneCountry.values()} : une entrée ajoutée à l'enum est couverte sans toucher à ce test.</p>
 */
class PhoneContractInvariantTest {

    // Source de vérité : PatientDTO.phone dans patient-service. Toute modification là-bas doit être répercutée ici — la dérive entre les deux copies est détectée par PatientDTOPhonePatternTest, côté patient-service.
    private static final String PATIENT_DTO_PHONE_REGEX = "^(\\+(33|32|41|44|39|1)[0-9]{8,11})?$";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (factory != null) {
            factory.close();
        }
    }

    /**
     * Porteur minimal de la contrainte réelle : on valide via le moteur Bean Validation, pas via {@code String.matches}, pour exercer le même mécanisme que patient-service.
     */
    private record PhoneHolder(@Pattern(regexp = PATIENT_DTO_PHONE_REGEX) String phone) {
    }

    @DisplayName("Tout numéro accepté par PhoneNormalizer satisfait la contrainte phone de PatientDTO")
    @ParameterizedTest(name = "{0}")
    @EnumSource(PhoneCountry.class)
    void normalized_number_satisfies_patient_dto_constraint(PhoneCountry country) {
        String nationalNumber = nationalNumberOf(country);

        PhoneNormalizer.Result result = PhoneNormalizer.normalize(nationalNumber, country);

        // Si la fixture elle-même est refusée, c'est le constructeur de numéro qui est cassé, pas le contrat entre services : on le distingue explicitement.
        assertThat(result.isValid())
                .as("Fixture invalide pour %s (%s) : PhoneNormalizer a refusé \"%s\" avec le message \"%s\"",
                        country.name(), country.label(), nationalNumber, result.errorMessage())
                .isTrue();

        Set<ConstraintViolation<PhoneHolder>> violations =
                validator.validate(new PhoneHolder(result.e164()));

        assertThat(violations)
                .as("Rupture du contrat pour %s (%s) : PhoneNormalizer produit \"%s\" (%d chiffres nationaux, indicatif %s), rejeté par la contrainte phone de PatientDTO",
                        country.name(), country.label(), result.e164(),
                        country.nationalDigits(), country.dialingCode())
                .isEmpty();
    }

    /**
     * Construit un numéro national de longueur exactement {@code nationalDigits()}, sans 0 de préfixe : {@link PhoneNormalizer} retire un éventuel 0 initial, ce qui raccourcirait le numéro d'un chiffre.
     */
    private static String nationalNumberOf(PhoneCountry country) {
        return "6".repeat(country.nationalDigits());
    }
}
