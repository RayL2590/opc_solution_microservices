package com.medilabo.patientservice.service;

import com.medilabo.patientservice.dto.PatientDTO;
import com.medilabo.patientservice.exception.PatientNotFoundException;
import com.medilabo.patientservice.model.Patient;
import com.medilabo.patientservice.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    // ---- Story 2.3: write paths (create / update) ----

    private PatientDTO validDto() {
        return PatientDTO.builder()
                .firstName("Jean")
                .lastName("Dupont")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .build();
    }

    @Test
    void createPatient_mapsDtoAndReturnsDtoWithAssignedId() {
        // Repo assigns the surrogate id on save.
        given(patientRepository.save(any(Patient.class))).willAnswer(invocation -> {
            Patient toSave = invocation.getArgument(0);
            toSave.setId(42L);
            return toSave;
        });

        PatientDTO result = patientService.createPatient(validDto());

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getLastName()).isEqualTo("Dupont");
        assertThat(result.getGender()).isEqualTo("M");
    }

    @Test
    void updatePatient_existingId_fullReplaceAppliesAllFields() {
        // Existing row has address/phone set; the incoming DTO omits them (null) →
        // full-replace must overwrite them to null (documented PUT contract, not PATCH).
        Patient existing = samplePatient();
        existing.setAddress("1 rue Ancienne");
        existing.setPhone("0102030405");
        given(patientRepository.findById(1L)).willReturn(Optional.of(existing));
        given(patientRepository.save(any(Patient.class))).willAnswer(inv -> inv.getArgument(0));

        patientService.updatePatient(1L, validDto());

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        org.mockito.Mockito.verify(patientRepository).save(captor.capture());
        Patient saved = captor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Jean");
        assertThat(saved.getLastName()).isEqualTo("Dupont");
        assertThat(saved.getGender()).isEqualTo("M");
        // Absent optional fields are overwritten to null — full-replace semantics.
        assertThat(saved.getAddress()).isNull();
        assertThat(saved.getPhone()).isNull();
    }

    @Test
    void updatePatient_missingId_throwsPatientNotFoundException() {
        given(patientRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.updatePatient(999L, validDto()))
                .isInstanceOf(PatientNotFoundException.class)
                .hasMessageContaining("999");
    }
}
