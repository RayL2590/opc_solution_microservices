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
    @Pattern(regexp = "^[MF]$", message = "Le genre doit être M ou F")
    private String gender;

    @Size(max = 255, message = "L'adresse ne doit pas dépasser 255 caractères")
    private String address;

    // Le front normalise en E.164 (+33..., +32..., +41..., +44..., +39..., +1...) avant d'appeler ce service ; on garde ici un garde-fou de format pour les appels API directs.
    // Les indicatifs sont dupliqués depuis PhoneCountry (front-service) : toute entrée ajoutée là-bas doit l'être ici, sinon le front produit un E.164 que ce service rejette en 400.
    // Le "?" du regex tolère la valeur vide (champ optionnel, pas de @NotBlank).
    @Pattern(regexp = "^(\\+(33|32|41|44|39|1)[0-9]{8,11})?$",
            message = "Le téléphone doit être au format international (ex. +33601020304)")
    private String phone;
}
