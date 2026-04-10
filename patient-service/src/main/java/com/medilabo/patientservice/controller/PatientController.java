package com.medilabo.patientservice.controller;

import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    // GET /patients → liste de tous les patients
    @GetMapping
    public ResponseEntity<List<PatientDTO>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    // GET /patients/{id} → détail d'un patient
    @GetMapping("/{id}")
    public ResponseEntity<PatientDTO> getPatientById(@PathVariable Integer id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    // POST /patients → créer un patient
    @PostMapping
    public ResponseEntity<PatientDTO> createPatient(
            @Valid @RequestBody PatientDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(patientService.createPatient(dto));
    }

    // PUT /patients/{id} → mettre à jour un patient
    @PutMapping("/{id}")
    public ResponseEntity<PatientDTO> updatePatient(
            @PathVariable Integer id,
            @Valid @RequestBody PatientDTO dto) {
        return ResponseEntity.ok(patientService.updatePatient(id, dto));
    }
}
