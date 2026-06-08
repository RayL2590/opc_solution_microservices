package com.medilabo.patientservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single per-service error envelope (RFC 7807 {@link ProblemDetail}).
 *
 * <p>This is the only {@code @RestControllerAdvice} in the service. After Story 2.3 it
 * maps every status the request paths can produce, so the "single per-service envelope"
 * invariant holds for all of them:
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} → 400 with a field-keyed {@code errors} map (Story 2.3, FR-3/FR-4)</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 for a malformed {@code {id}} path variable (Story 2.3)</li>
 *   <li>{@link PatientNotFoundException} → 404 (Story 2.2)</li>
 *   <li>any uncaught {@link Exception} → 500 minimal envelope, no stack trace leaked (Story 2.3)</li>
 * </ul>
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

    /**
     * Bean Validation failure on {@code @Valid @RequestBody PatientDTO}. Returns a 400
     * {@code ProblemDetail} with an {@code errors} property: a field-name → French message
     * map. Tests assert on the map KEYS + status, never the localized text.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // First message wins on duplicate field keys (deterministic, insertion-ordered).
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        // Log only the field KEYS — the values may echo submitted input; keys are field names, not PII.
        log.warn("Validation failed on fields: {}", errors.keySet());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La validation du patient a échoué");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Path-variable conversion failure (e.g. a non-numeric or out-of-{@code Long}-range
     * {@code {id}}) — thrown before the controller runs. Mapped to a 400 {@code ProblemDetail}
     * so a malformed id stays inside the RFC 7807 envelope instead of Spring's legacy 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // Log the parameter NAME only — never the raw submitted value.
        log.warn("Type mismatch on path variable: {}", ex.getName());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Paramètre invalide : " + ex.getName());
    }

    /**
     * Catch-all for any otherwise-unmapped exception (e.g. a {@code DataAccessException}
     * from the JPA layer). Returns a 500 minimal envelope — no stack trace, no exception
     * class name leaked to the client. The full exception is logged server-side only.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        log.error("Unhandled exception on patient request", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
    }
}
