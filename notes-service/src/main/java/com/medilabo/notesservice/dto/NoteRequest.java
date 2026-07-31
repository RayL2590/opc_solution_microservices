package com.medilabo.notesservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NoteRequest {

    @NotNull(message = "L'identifiant du patient est obligatoire")
    private Integer patId;

    // Nom dénormalisé sur chaque note (D-DATA-3) pour éviter un second appel à patient-service —
    // c'est ce qui s'affiche dans la timeline. Obligatoire, comme le texte de la note.
    @NotBlank(message = "Le nom du patient est obligatoire")
    private String patient;

    @NotBlank(message = "Le texte de la note ne peut pas être vide")
    private String note;
}
