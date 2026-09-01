package com.medilabo.notesservice.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

// Pas de @Data sur un document Mongo : equals/hashCode générés sur des champs muables, c'est un footgun.
@Document(collection = "note")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Note {

    @Id
    private String id;

    // Indexé parce que findByPatIdOrderByCreatedAtDescIdDesc est la requête la plus fréquente.
    // Sans index Mongo scanne toute la collection à chaque ouverture de fiche patient.
    @Indexed
    private Integer patId;

    private String patient;
    private String note;

    // Rempli par @EnableMongoAuditing à l'insert, ne pas y toucher à la main.
    @CreatedDate
    private Instant createdAt;
}
