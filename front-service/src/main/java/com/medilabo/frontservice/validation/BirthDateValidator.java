package com.medilabo.frontservice.validation;

import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valide une date de naissance : pas dans le futur (aujourd'hui autorisé) et pas plus de
 * {@code maxAgeYears} ans. Le caractère obligatoire (non null) est porté par {@code @NotNull}.
 * Miroir de patient-service (pas de module partagé).
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
