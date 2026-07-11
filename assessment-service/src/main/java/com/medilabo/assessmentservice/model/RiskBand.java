package com.medilabo.assessmentservice.model;

/**
 * Les quatre classifications de risque (PRD §3).
 *
 * <p>L'ordre de déclaration est l'ordre de sévérité — {@code NONE < BORDERLINE < IN_DANGER
 * < EARLY_ONSET} — donc "la bande la plus haute gagne en cas de chevauchement" (FR-9) se
 * résout par un max sur {@link #ordinal()}. {@link #getDisplayName()} porte la chaîne exacte
 * attendue par FR-8.</p>
 */
public enum RiskBand {

    NONE("None"),
    BORDERLINE("Borderline"),
    IN_DANGER("In Danger"),
    EARLY_ONSET("Early Onset");

    private final String displayName;

    RiskBand(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return la chaîne exacte du champ {@code riskBand} attendu par FR-8.
     */
    public String getDisplayName() {
        return displayName;
    }
}
