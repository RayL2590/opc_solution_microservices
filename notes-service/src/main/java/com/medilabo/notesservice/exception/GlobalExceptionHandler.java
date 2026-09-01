package com.medilabo.notesservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), f -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }
        log.warn("Validation failed on fields: {}", errors.keySet(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "La validation de la note a échoué");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(NoteNotFoundException.class)
    public ProblemDetail handleNoteNotFound(NoteNotFoundException ex) {
        log.warn("Note not found: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on parameter: {}", ex.getName(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Paramètre invalide : " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Corps de requête illisible ou malformé");
    }

    /** Verbe non exposé sur la route : 405, jamais 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non supportée : " + ex.getMethod());
    }

    /**
     * Content-Type non géré (ex. text/plain sur une API JSON) : 415, jamais 500.
     * Sans ce handler l'exception tombait dans le catch-all : un appelant qui se trompe d'en-tête recevait une erreur serveur alors que la faute est de son côté.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Type de contenu non supporté");
    }

    /**
     * {@code Accept} demandant un format qu'on ne produit pas (ex. application/xml) : 406.
     * Pendant de {@link HttpMediaTypeNotSupportedException} côté réponse.
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("No acceptable representation for Accept header", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_ACCEPTABLE, "Aucune représentation disponible pour l'en-tête Accept");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        log.error("Unhandled exception on notes request", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
    }
}
