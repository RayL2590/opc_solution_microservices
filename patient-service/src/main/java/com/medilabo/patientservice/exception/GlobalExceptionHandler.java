package com.medilabo.patientservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    public ProblemDetail handlePatientNotFound(PatientNotFoundException ex) {
        log.warn("Patient not found: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation failed on fields: {}", errors.keySet(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La validation du patient a échoué");
        problem.setProperty("errors", errors);
        return problem;
    }


    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on path variable: {}", ex.getName(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Paramètre invalide : " + ex.getName());
    }

    /**
     * Corps illisible : JSON malformé, ou date au mauvais format ("31/12/1990" au lieu de
     * l'ISO "1990-12-31"). C'est l'appelant qui est fautif, donc 400 — sans ce handler
     * l'exception tombait dans le catch-all et sortait en 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Corps de requête illisible ou malformé");
    }

    /** Verbe non exposé sur la route (ex. DELETE /patients/{id}) : 405, jamais 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non supportée : " + ex.getMethod());
    }

    /** Content-Type non géré (ex. text/plain sur une API JSON) : 415, jamais 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporté");
    }

    /**
     * {@code Accept} demandant un format qu'on ne produit pas (ex. application/xml) : 406.
     * Pendant de {@link HttpMediaTypeNotSupportedException} côté réponse — même principe,
     * c'est l'appelant qui demande l'impossible, pas le serveur qui échoue.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("No acceptable representation for Accept header", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_ACCEPTABLE, "Aucune représentation disponible pour l'en-tête Accept");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        log.error("Unhandled exception on patient request", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
    }
}
