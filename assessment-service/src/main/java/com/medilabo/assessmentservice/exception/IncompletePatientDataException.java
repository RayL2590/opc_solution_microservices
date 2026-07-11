package com.medilabo.assessmentservice.exception;

public class IncompletePatientDataException extends RuntimeException {

    public IncompletePatientDataException(Integer patientId) {
        super("Patient id=" + patientId + " is missing required field dateOfBirth");
    }
}
