package com.medilabo.frontservice.dto;

/**
 * Pays supportés pour la saisie de téléphone (démo : périmètre volontairement réduit).
 * Chaque entrée porte son indicatif E.164 et le nombre de chiffres attendu APRÈS l'indicatif
 * (numéro national sans le 0 de préfixe), utilisé pour une validation de longueur simple.
 *
 * <p>Pas de dépendance à libphonenumber : normalisation maison suffisante pour ce périmètre.</p>
 */
public enum PhoneCountry {

    FR("+33", "France", 9),
    BE("+32", "Belgique", 9),
    CH("+41", "Suisse", 9),
    UK("+44", "Royaume-Uni", 10),
    IT("+39", "Italie", 10);

    private final String dialingCode;
    private final String label;
    private final int nationalDigits;

    PhoneCountry(String dialingCode, String label, int nationalDigits) {
        this.dialingCode = dialingCode;
        this.label = label;
        this.nationalDigits = nationalDigits;
    }

    /** Indicatif au format E.164, ex. {@code "+33"}. */
    public String dialingCode() {
        return dialingCode;
    }

    /** Libellé lisible pour le sélecteur, ex. {@code "France"}. */
    public String label() {
        return label;
    }

    /** Nombre de chiffres du numéro national (sans le 0 de préfixe), ex. 9 pour la France. */
    public int nationalDigits() {
        return nationalDigits;
    }

    /** Chiffres de l'indicatif sans le {@code +}, ex. {@code "33"} pour la France. */
    public String countryCodeDigits() {
        return dialingCode.substring(1);
    }
}
