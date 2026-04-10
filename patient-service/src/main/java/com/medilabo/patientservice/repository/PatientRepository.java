package com.medilabo.patientservice.repository;

import com.medilabo.patientservice.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Integer> {
    // JpaRepository fournit déjà : findAll, findById, save, deleteById
}
