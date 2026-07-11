package com.medilabo.assessmentservice.exception;

public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
