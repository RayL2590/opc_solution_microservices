package com.medilabo.notesservice.dto;

import com.medilabo.notesservice.model.Note;

import java.time.Instant;

// createdAt lu depuis l'objet retourné par repository.save() — @CreatedDate l'écrase à l'insert.
public record NoteDTO(String id, Integer patId, String patient, String note, Instant createdAt) {

    public static NoteDTO from(Note saved) {
        return new NoteDTO(saved.getId(), saved.getPatId(), saved.getPatient(),
                saved.getNote(), saved.getCreatedAt());
    }
}
