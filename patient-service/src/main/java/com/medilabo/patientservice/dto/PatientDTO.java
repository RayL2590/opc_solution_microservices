package com.medilabo.patientservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDTO {

    private Integer id;

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    private String gender;

    private String address; // optionnel
    private String phone;   // optionnel
}
