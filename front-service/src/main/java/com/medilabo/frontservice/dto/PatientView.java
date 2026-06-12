package com.medilabo.frontservice.dto;

import java.time.LocalDate;

/**
 * DTO côté front (pas de module partagé — duplication intentionnelle, frontière polyglotte).
 * id en Long : évite la troncature silencieuse pour id > Integer.MAX_VALUE.
 */
public record PatientView(
        Long id,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String phone
) {}
