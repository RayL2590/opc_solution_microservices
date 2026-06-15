package com.medilabo.frontservice.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Date de naissance plausible : non nulle, pas dans le futur (aujourd'hui autorisé),
 * et pas plus de {@link #maxAgeYears()} ans. Dupliqué côté front (pas de module partagé) —
 * miroir de patient-service pour un retour utilisateur immédiat.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BirthDateValidator.class)
public @interface BirthDate {

    String message() default "La date de naissance doit être dans le passé et l'âge inférieur à 160 ans";

    int maxAgeYears() default 160;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
