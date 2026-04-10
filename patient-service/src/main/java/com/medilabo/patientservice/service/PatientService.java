package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    // Récupérer tous les patients
    public List<PatientDTO> getAllPatients() {
        return patientRepository.findAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Récupérer un patient par son id
    public PatientDTO getPatientById(Integer id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Patient introuvable avec l'id : " + id));
        return toDTO(patient);
    }

    public PatientDTO createPatient(PatientDTO dto) {
        Patient patient = toEntity(dto);
        return toDTO(patientRepository.save(patient));
    }

    public PatientDTO updatePatient(Integer id, PatientDTO dto) {
        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Patient introuvable avec l'id : " + id));

        existing.setFirstName(dto.getFirstName());
        existing.setLastName(dto.getLastName());
        existing.setDateOfBirth(dto.getDateOfBirth());
        existing.setGender(dto.getGender());
        existing.setAddress(dto.getAddress());
        existing.setPhone(dto.getPhone());

        return toDTO(patientRepository.save(existing));
    }

    private PatientDTO toDTO(Patient p) {
        return PatientDTO.builder()
                .id(p.getId())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .dateOfBirth(p.getDateOfBirth())
                .gender(p.getGender())
                .address(p.getAddress())
                .phone(p.getPhone())
                .build();
    }

    private Patient toEntity(PatientDTO dto) {
        return Patient.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .dateOfBirth(dto.getDateOfBirth())
                .gender(dto.getGender())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .build();
    }
}
