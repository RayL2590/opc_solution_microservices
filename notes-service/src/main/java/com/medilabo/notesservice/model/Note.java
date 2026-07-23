package com.medilabo.notesservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// @Data interdit sur les documents Mongo : equals/hashCode généré sur champs muables = footgun.
@Document(collection = "note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    private String id;

    // Indexé : findByPatIdOrderByCreatedAtDesc est la requête la plus fréquente du service.
    // Sans index, Mongo balaie toute la collection à chaque lecture de fiche patient.
    @Indexed
    private Integer patId;

    private String patient;
    private String note;

    // Peuplé par @EnableMongoAuditing au moment de l'insert — ne jamais affecter manuellement.
    @CreatedDate
    private Instant createdAt;
}
