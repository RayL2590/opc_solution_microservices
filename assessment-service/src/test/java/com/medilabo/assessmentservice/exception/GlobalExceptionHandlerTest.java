package com.medilabo.assessmentservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void upstreamNotFound_returns404() {
        ProblemDetail detail = handler.handleUpstreamNotFound(
                new UpstreamNotFoundException("Patient introuvable : id=42"));
        assertEquals(404, detail.getStatus());
    }

    @Test
    void badGateway_returns502() {
        ProblemDetail detail = handler.handleBadGateway(
                new BadGatewayException("Erreur upstream patient-service pour id=42"));
        assertEquals(502, detail.getStatus());
    }

    @Test
    void gatewayTimeout_returns504() {
        ProblemDetail detail = handler.handleGatewayTimeout(
                new GatewayTimeoutException("patient-service inaccessible pour id=42",
                        new RuntimeException("connection refused")));
        assertEquals(504, detail.getStatus());
    }

    @Test
    void incompletePatientData_returns422() {
        ProblemDetail detail = handler.handleIncompletePatientData(
                new IncompletePatientDataException(42));
        assertEquals(422, detail.getStatus());
    }

    @Test
    void uncaughtException_returns500() {
        ProblemDetail detail = handler.handleUncaught(new RuntimeException("unexpected"));
        assertEquals(500, detail.getStatus());
    }
}
