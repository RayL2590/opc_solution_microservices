package com.medilabo.patientservice.repository;

import com.medilabo.patientservice.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

/**
 * Intégration @DataJpaTest contre MySQL réel (AutoConfigureTestDatabase.Replace.NONE) —
 * vérifie les 4 patients canoniques (ids 1..4). Requiert SPRING_DATASOURCE_PASSWORD.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patientRepository;

    @Test
    void seedsTheFourCanonicalPatientsOnFreshBoot() {
        List<Patient> patients = patientRepository.findAll(Sort.by("id").ascending());

        assertThat(patients).hasSize(4);
        assertThat(patients).extracting(Patient::getId)
                .containsExactly(1L, 2L, 3L, 4L);
        assertThat(patients).extracting(Patient::getLastName)
                .containsExactly("TestNone", "TestBorderline", "TestInDanger", "TestEarlyOnset");
    }

    @Test
    void findById_existingId_returnsThePatient() {
        Optional<Patient> patient = patientRepository.findById(1L);

        assertThat(patient).isPresent();
        assertThat(patient.get().getLastName()).isEqualTo("TestNone");
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        assertThat(patientRepository.findById(999L)).isEmpty();
    }
}
