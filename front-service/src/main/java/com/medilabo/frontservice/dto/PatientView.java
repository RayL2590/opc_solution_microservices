package com.medilabo.frontservice.dto;

import java.time.LocalDate;

/**
 * Consumer-side DTO mirroring {@code patient-service}'s {@code PatientDTO} (Story 5.2, FR-10).
 *
 * <p>Duplicated here intentionally — no shared Java module (architecture §"Polyglot persistence
 * boundary"). {@code id} is {@code Long} to match the patient-service entity and avoid silent
 * truncation when deserializing JSON numbers larger than {@code Integer.MAX_VALUE}
 * (Long-widening defer resolved — deferred-work.md, code review of story 2-1).
 *
 * <p>No validation annotations: this record is display-only (read path only, no user input).
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
