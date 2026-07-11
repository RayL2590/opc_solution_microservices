package com.medilabo.frontservice.util;

import com.medilabo.frontservice.dto.PhoneCountry;

/**
 * Normalise un numéro de téléphone saisi librement vers la forme canonique E.164
 * (ex. {@code +33601020304}), afin qu'une seule et unique représentation soit stockée
 * en base quelle que soit la saisie de l'utilisateur.
 *
 * <p>Formats d'entrée tolérés (exemples pour la France, indicatif {@code +33}) :</p>
 * <ul>
 *   <li>{@code 0601020304}          → {@code +33601020304} (0 national remplacé par l'indicatif)</li>
 *   <li>{@code 06 01 02 03 04}      → {@code +33601020304} (espaces ignorés)</li>
 *   <li>{@code 06.01.02.03.04}      → {@code +33601020304} (séparateurs ignorés)</li>
 *   <li>{@code +33601020304}        → {@code +33601020304} (déjà international, inchangé)</li>
 *   <li>{@code 0033601020304}       → {@code +33601020304} (préfixe 00 international)</li>
 *   <li>{@code +33 0601020304}      → {@code +33601020304} (0 national après indicatif toléré)</li>
 * </ul>
 *
 * <p>Périmètre volontairement réduit (démo) : pas de dépendance à libphonenumber,
 * pas de validation exhaustive des plans de numérotation par opérateur.</p>
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {
    }

    /** Résultat de la normalisation : soit un E.164 valide, soit une erreur explicite. */
    public record Result(String e164, String errorMessage) {

        public boolean isValid() {
            return errorMessage == null;
        }

        static Result ok(String e164) {
            return new Result(e164, null);
        }

        static Result error(String message) {
            return new Result(null, message);
        }
    }

    /**
     * Normalise {@code rawPhone} vers E.164 en s'appuyant sur {@code country} pour lever
     * l'ambiguïté des numéros nationaux (préfixe 0).
     *
     * @param rawPhone saisie brute (peut être {@code null}/vide — le téléphone est optionnel)
     * @param country  pays sélectionné dans le formulaire ; requis dès que {@code rawPhone} est renseigné
     * @return un {@link Result} : {@code e164} renseigné si valide, sinon {@code errorMessage}.
     *         Une entrée vide est considérée valide et produit un {@code e164} vide (champ optionnel).
     */
    public static Result normalize(String rawPhone, PhoneCountry country) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return Result.ok(""); // champ optionnel : vide reste vide
        }
        if (country == null) {
            return Result.error("Veuillez sélectionner un indicatif pays pour le téléphone");
        }

        String trimmed = rawPhone.trim();
        boolean explicitInternational = trimmed.startsWith("+") || trimmed.startsWith("00");

        String digits = trimmed.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Result.error("Le numéro de téléphone doit contenir des chiffres");
        }

        String nationalDigits;
        if (explicitInternational) {
            // Retire un éventuel préfixe 00, puis l'indicatif pays attendu.
            String withoutIntlPrefix = digits.startsWith("00") ? digits.substring(2) : digits;
            String cc = country.countryCodeDigits();
            if (!withoutIntlPrefix.startsWith(cc)) {
                return Result.error(
                        "Le numéro ne correspond pas à l'indicatif " + country.dialingCode()
                                + " (" + country.label() + ")");
            }
            nationalDigits = withoutIntlPrefix.substring(cc.length());
            // Certains collent un 0 national après l'indicatif (+33 0601...) : on le tolère.
            if (nationalDigits.startsWith("0")) {
                nationalDigits = nationalDigits.substring(1);
            }
        } else {
            // Saisie nationale : un éventuel 0 de préfixe est remplacé par l'indicatif.
            nationalDigits = digits.startsWith("0") ? digits.substring(1) : digits;
        }

        if (nationalDigits.length() != country.nationalDigits()) {
            return Result.error(
                    "Le numéro de téléphone doit contenir " + country.nationalDigits()
                            + " chiffres pour " + country.label());
        }

        return Result.ok(country.dialingCode() + nationalDigits);
    }
}
