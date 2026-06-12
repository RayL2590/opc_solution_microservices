package com.medilabo.frontservice.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Objet de commande mutable (Thymeleaf th:field exige des setters — un record casserait le binding).
 * @DateTimeFormat(ISO.DATE) obligatoire : input[type=date] soumet yyyy-MM-dd,
 * sans ça Spring MVC ne peut pas convertir en LocalDate (400 silencieux).
 */
@Data
public class PatientForm {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    private String gender;

    private String address;

    private String phone;
}
