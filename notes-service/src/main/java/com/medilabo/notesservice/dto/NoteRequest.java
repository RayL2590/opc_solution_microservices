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

    private String patient;

    @NotBlank(message = "Le texte de la note ne peut pas être vide")
    private String note;
}
