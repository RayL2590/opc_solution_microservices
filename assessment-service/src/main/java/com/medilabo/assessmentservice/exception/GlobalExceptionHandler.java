package com.medilabo.assessmentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UpstreamNotFoundException.class)
    public ProblemDetail handleUpstreamNotFound(UpstreamNotFoundException ex) {
        log.warn("Upstream resource not found: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadGatewayException.class)
    public ProblemDetail handleBadGateway(BadGatewayException ex) {
        log.warn("Upstream service error: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ProblemDetail handleGatewayTimeout(GatewayTimeoutException ex) {
        log.warn("Upstream timeout or unreachable: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(IncompletePatientDataException.class)
    public ProblemDetail handleIncompletePatientData(IncompletePatientDataException ex) {
        log.warn("Incomplete patient data from upstream: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
    }

    /**
     * patId non convertible en Integer (ex. /assessments/abc) : la faute est à l'appelant,
     * donc 400. Sans ce handler l'exception tombait dans le catch-all et sortait en 500 —
     * incohérent avec patient-service et notes-service, qui rendent bien 400 sur ce cas.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch on path variable: {}", ex.getName(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Paramètre invalide : " + ex.getName());
    }

    /** Verbe non exposé sur la route : 405, jamais 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod(), ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non supportée : " + ex.getMethod());
    }

    /** {@code Accept} demandant un format qu'on ne produit pas (ex. application/xml) : 406. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("No acceptable representation for Accept header", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_ACCEPTABLE, "Aucune représentation disponible pour l'en-tête Accept");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        log.error("Unhandled exception on assessment request", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
    }
}
