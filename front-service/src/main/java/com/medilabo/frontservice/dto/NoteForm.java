package com.medilabo.frontservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Objet de commande mutable (Thymeleaf th:field exige des setters).
 * patId et patient sont fixés par le contrôleur (path {id} + PatientView déjà chargé),
 * jamais saisis par le client — seul `note` est un champ de formulaire.
 */
@Data
public class NoteForm {

    private Integer patId;

    private String patient;

    @NotBlank(message = "La note ne peut pas être vide")
    private String note;
}
