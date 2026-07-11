package com.medilabo.assessmentservice.exception;

public class UpstreamNotFoundException extends RuntimeException {

    public UpstreamNotFoundException(String message) {
        super(message);
    }
}
