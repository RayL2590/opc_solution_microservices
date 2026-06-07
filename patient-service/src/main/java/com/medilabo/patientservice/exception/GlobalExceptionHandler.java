package com.medilabo.patientservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single per-service error envelope (RFC 7807 {@link ProblemDetail}).
 *
 * <p>Story 2.2 owns the not-found mapping: {@link PatientNotFoundException} → 404.
 * Validation (400) and other mappings are introduced by later stories (2.3+).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    public ProblemDetail handlePatientNotFound(PatientNotFoundException ex) {
        // Message contains only the patientId — safe to log, no PII.
        log.warn("Patient not found: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
