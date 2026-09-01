package com.medilabo.patientservice.repository;

import com.medilabo.patientservice.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
}
