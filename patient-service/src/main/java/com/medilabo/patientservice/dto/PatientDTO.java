package com.medilabo.patientservice.dto;

import com.medilabo.patientservice.validation.BirthDate;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDTO {

    private Long id;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    @BirthDate
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    @Pattern(regexp = "^[MFU]$", message = "Le genre doit être M, F ou U")
    private String gender;

    private String address; // optionnel
    private String phone;   // optionnel
}
