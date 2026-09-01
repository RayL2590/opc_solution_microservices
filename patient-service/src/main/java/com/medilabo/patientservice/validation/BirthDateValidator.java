package com.medilabo.patientservice.validation;

import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valide une date de naissance : pas dans le futur (aujourd'hui autorisé) et pas plus de {@code maxAgeYears} ans. Le null est géré par {@code @NotNull} ailleurs, donc ici une valeur nulle est considérée valide — pas la peine de dupliquer le message.
 */
public class BirthDateValidator implements ConstraintValidator<BirthDate, LocalDate> {

    private int maxAgeYears;

    @Override
    public void initialize(BirthDate constraint) {
        this.maxAgeYears = constraint.maxAgeYears();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // laissé à @NotNull
        }
        LocalDate today = LocalDate.now();
        LocalDate oldestAllowed = today.minusYears(maxAgeYears);
        return !value.isAfter(today) && !value.isBefore(oldestAllowed);
    }
}
