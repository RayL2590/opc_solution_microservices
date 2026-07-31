package com.medilabo.assessmentservice.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Les quatre classifications de risque (PRD §3).
 *
 * <p>L'ordre de déclaration suit l'ordre de sévérité — {@code NONE < BORDERLINE < IN_DANGER
 * < EARLY_ONSET} — donc la règle "la bande la plus haute gagne en cas de chevauchement" (FR-9)
 * se résout juste avec un max sur {@link #ordinal()}. {@link #getDisplayName()} porte la chaîne
 * exacte attendue par FR-8. FR-8 et FR-9 sont définis dans
 * {@code Documentation/requirements-glossary.md}.</p>
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
     * @return la chaîne exacte du champ {@code riskBand} attendu par FR-8 ; {@code @JsonValue}
     *         en fait aussi la forme sérialisée de l'enum, jamais le nom de la constante.
     */
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
