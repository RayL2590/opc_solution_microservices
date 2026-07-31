package com.medilabo.frontservice.dto;

import java.time.LocalDate;

import com.medilabo.frontservice.validation.BirthDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @BirthDate
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    @Pattern(regexp = "^[MFU]$", message = "Le genre doit être M, F ou U")
    private String gender;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
    private String address;

    /**
     * Pays de l'indicatif choisi dans le formulaire, sert à normaliser {@link #phone}
     * vers E.164 (voir {@code PhoneNormalizer}). Défaut FR sur un formulaire vierge.
     */
    private PhoneCountry phoneCountry = PhoneCountry.FR;

    /**
     * Téléphone tel que saisi (format libre). Normalisé en E.164 par le contrôleur
     * avant l'envoi vers patient-service. Pas de @Pattern ici : la normalisation
     * tolère de toute façon espaces, points, +, 00, etc.
     */
    private String phone;
}
