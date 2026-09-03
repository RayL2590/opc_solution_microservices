package com.medilabo.frontservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.medilabo.frontservice.dto.PhoneCountry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Pattern;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

    /**
     * Les formats de saisie réellement offerts au praticien, tels que documentés par {@link PhoneNormalizer}. Chacun emprunte une branche différente du normalizer : saisie nationale nue, 0 national à retirer, indicatif explicite, préfixe international 00, séparateurs à ignorer.
     */
    private enum InputFormat {
        NATIONAL_NU {
            String render(PhoneCountry country, String national) {
                return national;
            }
        },
        ZERO_NATIONAL {
            String render(PhoneCountry country, String national) {
                return "0" + national;
            }
        },
        INDICATIF_EXPLICITE {
            String render(PhoneCountry country, String national) {
                return country.dialingCode() + national;
            }
        },
        PREFIXE_00 {
            String render(PhoneCountry country, String national) {
                return "00" + country.countryCodeDigits() + national;
            }
        },
        AVEC_SEPARATEURS {
            String render(PhoneCountry country, String national) {
                return "0" + String.join(" ", national.split(""));
            }
        };

        abstract String render(PhoneCountry country, String national);
    }

    @DisplayName("Tout numéro accepté par PhoneNormalizer satisfait la contrainte phone de PatientDTO")
    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("countriesTimesInputFormats")
    void normalized_number_satisfies_patient_dto_constraint(PhoneCountry country, InputFormat format) {
        String expectedE164 = country.dialingCode() + "6".repeat(country.nationalDigits());
        String rawInput = format.render(country, "6".repeat(country.nationalDigits()));

        PhoneNormalizer.Result result = PhoneNormalizer.normalize(rawInput, country);

        // Si la fixture elle-même est refusée, c'est le constructeur de numéro qui est cassé, pas le contrat entre services : on le distingue explicitement.
        assertThat(result.isValid())
                .as("Fixture invalide pour %s (%s), format %s : PhoneNormalizer a refusé \"%s\" avec le message \"%s\"",
                        country.name(), country.label(), format, rawInput, result.errorMessage())
                .isTrue();

        // Tous les formats de saisie doivent converger vers un seul E.164 : sans ça un chemin du normalizer pourrait produire un numéro plus court et rester dans la fourchette [8,11] de la regex, donc passer inaperçu.
        assertThat(result.e164())
                .as("Divergence de normalisation pour %s (%s) : le format %s produit \"%s\" au lieu de \"%s\"",
                        country.name(), country.label(), format, result.e164(), expectedE164)
                .isEqualTo(expectedE164);

        Set<ConstraintViolation<PhoneHolder>> violations =
                validator.validate(new PhoneHolder(result.e164()));

        assertThat(violations)
                .as("Rupture du contrat pour %s (%s) : PhoneNormalizer produit \"%s\" (%d chiffres nationaux, indicatif %s), rejeté par la contrainte phone de PatientDTO",
                        country.name(), country.label(), result.e164(),
                        country.nationalDigits(), country.dialingCode())
                .isEmpty();
    }

    private static Stream<Arguments> countriesTimesInputFormats() {
        return Arrays.stream(PhoneCountry.values())
                .flatMap(country -> Arrays.stream(InputFormat.values())
                        .map(format -> Arguments.of(country, format)));
    }

    /**
     * Le test ci-dessus prouve que la regex ACCEPTE les numéros légitimes ; il ne dit rien de ce qu'elle accepte en plus. Or son alternance d'indicatifs n'est pas ancrée sur une frontière : {@code +1} peut matcher le début d'un indicatif plus long, et la fourchette {@code [8,11]} absorbe le décalage.
     *
     * <p>Ce test documente la faiblesse au lieu de la laisser invisible. Il ne la corrige pas : la regex vit dans patient-service et son élargissement est une décision produit, pas un détail de test. Si l'alternance est un jour ancrée par longueur exacte, ce test échouera — et ce sera le signal que la limitation a été levée.</p>
     */
    @DisplayName("Limitation connue : la regex accepte des E.164 qu'aucun PhoneCountry ne produit")
    @ParameterizedTest(name = "{0} accepté par collision de préfixe")
    @ValueSource(strings = {
            "+13666666666",   // "+13" (Grenade) : l'alternance matche "1", il reste 10 chiffres — dans [8,11]
            "+166666666666",  // US avec 11 chiffres nationaux au lieu de 10
            "+336666666666"   // FR avec 11 chiffres nationaux au lieu de 9
    })
    void regex_also_accepts_numbers_no_country_can_produce(String uncoveredE164) {
        assertThat(validator.validate(new PhoneHolder(uncoveredE164)))
                .as("La contrainte de PatientDTO rejette désormais \"%s\". Si l'alternance d'indicatifs a été ancrée sur une longueur exacte, c'est une amélioration : retirer cette entrée et mettre à jour PATIENT_DTO_PHONE_REGEX",
                        uncoveredE164)
                .isEmpty();
    }
}
