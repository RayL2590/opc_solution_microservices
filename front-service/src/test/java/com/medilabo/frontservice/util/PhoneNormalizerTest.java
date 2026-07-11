package com.medilabo.frontservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.medilabo.frontservice.dto.PhoneCountry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNormalizerTest {

    @DisplayName("Tous les formats FR équivalents convergent vers le même E.164")
    @ParameterizedTest(name = "\"{0}\" -> +33601020304")
    @ValueSource(strings = {
            "0601020304",
            "06 01 02 03 04",
            "06.01.02.03.04",
            "06-01-02-03-04",
            "+33601020304",
            "+33 6 01 02 03 04",
            "0033601020304",
            "+33 0601020304"   // 0 national toléré après l'indicatif
    })
    void normalizes_all_french_formats_to_single_e164(String raw) {
        PhoneNormalizer.Result result = PhoneNormalizer.normalize(raw, PhoneCountry.FR);

        assertThat(result.isValid()).isTrue();
        assertThat(result.e164()).isEqualTo("+33601020304");
    }

    @Test
    @DisplayName("Champ vide : valide et laissé vide (téléphone optionnel)")
    void blank_phone_is_valid_and_empty() {
        assertThat(PhoneNormalizer.normalize("", PhoneCountry.FR).e164()).isEmpty();
        assertThat(PhoneNormalizer.normalize("   ", PhoneCountry.FR).isValid()).isTrue();
        assertThat(PhoneNormalizer.normalize(null, PhoneCountry.FR).isValid()).isTrue();
    }

    @Test
    @DisplayName("Numéro trop long (bug rapporté) : rejeté avec un message clair")
    void oversized_number_is_rejected() {
        PhoneNormalizer.Result result =
                PhoneNormalizer.normalize("060102030405060708", PhoneCountry.FR);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("9 chiffres");
    }

    @Test
    @DisplayName("Numéro trop court : rejeté")
    void too_short_number_is_rejected() {
        assertThat(PhoneNormalizer.normalize("0601", PhoneCountry.FR).isValid()).isFalse();
    }

    @Test
    @DisplayName("Indicatif international ne correspondant pas au pays sélectionné : rejeté")
    void mismatched_country_code_is_rejected() {
        PhoneNormalizer.Result result =
                PhoneNormalizer.normalize("+44601020304", PhoneCountry.FR);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("+33");
    }

    @Test
    @DisplayName("Autres pays supportés : normalisation correcte")
    void normalizes_other_supported_countries() {
        // Belgique : 9 chiffres nationaux
        assertThat(PhoneNormalizer.normalize("0470123456", PhoneCountry.BE).e164())
                .isEqualTo("+32470123456");
        // Suisse : 9 chiffres nationaux
        assertThat(PhoneNormalizer.normalize("079 123 45 67", PhoneCountry.CH).e164())
                .isEqualTo("+41791234567");
        // Royaume-Uni : 10 chiffres nationaux
        assertThat(PhoneNormalizer.normalize("07911123456", PhoneCountry.UK).e164())
                .isEqualTo("+447911123456");
        // Italie : 10 chiffres nationaux
        assertThat(PhoneNormalizer.normalize("3123456789", PhoneCountry.IT).e164())
                .isEqualTo("+393123456789");
    }

    @Test
    @DisplayName("Téléphone renseigné sans pays : rejeté avec message explicite")
    void phone_without_country_is_rejected() {
        PhoneNormalizer.Result result = PhoneNormalizer.normalize("0601020304", null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errorMessage()).contains("indicatif");
    }
}
