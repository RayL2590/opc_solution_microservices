package com.medilabo.frontservice.dto;

import java.time.LocalDate;

/**
 * DTO côté front : chaque service garde sa propre copie des concepts qu'il consomme, aucun
 * module partagé entre services (décision D-DATA-3, architecture.md).
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
