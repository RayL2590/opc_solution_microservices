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
 * Integration test for {@link PatientRepository} — Story 2.1 oracle for the canonical seed.
 *
 * <p>Boots a {@link DataJpaTest} slice against the real MySQL configured in
 * {@code application.properties} (no H2 substitution) so that {@code schema.sql} +
 * {@code data.sql} run exactly as in production. Requires the {@code SPRING_DATASOURCE_PASSWORD}
 * environment variable to authenticate against local MySQL (see project memory
 * {@code patient-service-db-secret}).
 *
 * <p>Asserts the NFR-D1 / NFR-D2 contract: a fresh boot yields exactly the four canonical
 * patients (TestNone, TestBorderline, TestInDanger, TestEarlyOnset) with ids 1..4, in that
 * order — Epic 4 Risk Assessment fixtures depend on this mapping.
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
