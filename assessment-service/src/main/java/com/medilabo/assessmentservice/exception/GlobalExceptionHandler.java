package com.medilabo.assessmentservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        log.error("Upstream service error: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    public ProblemDetail handleGatewayTimeout(GatewayTimeoutException ex) {
        log.error("Upstream timeout or unreachable: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(IncompletePatientDataException.class)
    public ProblemDetail handleIncompletePatientData(IncompletePatientDataException ex) {
        log.warn("Incomplete patient data from upstream: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaught(Exception ex) {
        log.error("Unhandled exception on assessment request", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur");
    }
}
