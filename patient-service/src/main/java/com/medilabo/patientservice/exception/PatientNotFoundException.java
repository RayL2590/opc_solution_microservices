package com.medilabo.patientservice.exception;

/**
 * Thrown when a patient lookup by id finds no matching row.
 *
 * <p>Mapped to an RFC 7807 {@code ProblemDetail} 404 by {@link GlobalExceptionHandler}.
 * The message carries only the {@code patientId} — never PII (name, address, phone).
 */
public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(Long id) {
        super("Patient introuvable avec l'id : " + id);
    }
}
