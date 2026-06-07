package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Pure-Mockito unit test for {@link PatientService} read methods — Story 2.2.
 *
 * <p>Proves the service-layer translation the {@code @WebMvcTest} slice mocks away:
 * a present row maps to a {@link PatientDTO}, and an absent row ({@code Optional.empty()})
 * raises {@link PatientNotFoundException} (which {@code GlobalExceptionHandler} maps to 404).
 * No Spring context, no DataSource.
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientService patientService;

    private Patient samplePatient() {
        return Patient.builder()
                .id(1L)
                .firstName("Test")
                .lastName("TestNone")
                .dateOfBirth(LocalDate.of(1966, 12, 31))
                .gender("F")
                .build();
    }

    @Test
    void getAllPatients_mapsEntitiesToDtos() {
        given(patientRepository.findAll()).willReturn(List.of(samplePatient()));

        List<PatientDTO> result = patientService.getAllPatients();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getLastName()).isEqualTo("TestNone");
    }

    @Test
    void getPatientById_existingId_returnsDto() {
        given(patientRepository.findById(1L)).willReturn(Optional.of(samplePatient()));

        PatientDTO result = patientService.getPatientById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getLastName()).isEqualTo("TestNone");
    }

    @Test
    void getPatientById_missingId_throwsPatientNotFoundException() {
        given(patientRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatientById(999L))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("999");
    }
}
