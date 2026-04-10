package com.medilabo.patientservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "patient")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotBlank(message = "Le prénom est obligatoire")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @NotNull(message = "La date de naissance est obligatoire")
    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @NotBlank(message = "Le genre est obligatoire")
    @Column(name = "gender", nullable = false, length = 1)
    private String gender; // "M" ou "F"

    @Column(name = "address", length = 255)
    private String address; 

    @Column(name = "phone", length = 20)
    private String phone; 
}
