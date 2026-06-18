package com.medilabo.patientservice.exception;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(Long id) {
        super("Patient introuvable avec l'id : " + id);
    }
}
