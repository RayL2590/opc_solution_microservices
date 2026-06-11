package com.medilabo.frontservice.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Mutable command object for the add-patient form (Story 5.4, FR-12).
 *
 * <p>Must be a mutable class — Thymeleaf {@code th:field} requires setter methods
 * ({@code setFirstName(…)}, etc.) at binding time. A Java record only exposes read
 * accessors and would break the {@code POST /ui/patients} binding silently.
 *
 * <p>{@code @DateTimeFormat(iso = ISO.DATE)} on {@code dateOfBirth} is mandatory:
 * an HTML {@code <input type="date">} submits the value as {@code yyyy-MM-dd}; without
 * this annotation, Spring MVC's ConversionService cannot coerce that string to a
 * {@link LocalDate} and every submission fails with 400.
 *
 * <p>Validation messages mirror {@code patient-service}'s {@code PatientDTO} constraints
 * so that client-side and server-side rejections carry identical French copy.
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
