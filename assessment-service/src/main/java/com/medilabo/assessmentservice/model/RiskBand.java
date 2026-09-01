package com.medilabo.assessmentservice.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Les quatre classifications de risque de diabète.
 *
 * <p>L'ordre de déclaration suit l'ordre de sévérité — {@code NONE < BORDERLINE < IN_DANGER < EARLY_ONSET}, mais ce n'est que de la lisibilité : la règle "la bande la plus haute gagne en cas de chevauchement" est portée par l'ordre des tests dans {@code RiskCalculator#classify}, pas par {@link #ordinal()}, sur lequel aucun code ne s'appuie. {@link #getDisplayName()} porte la chaîne exacte attendue sur le fil HTTP par l'API d'évaluation de risque.</p>
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
     * @return la chaîne exacte attendue pour le champ {@code riskBand} de la réponse HTTP ; {@code @JsonValue} en fait aussi la forme sérialisée de l'enum, jamais le nom de la constante.
     */
    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
