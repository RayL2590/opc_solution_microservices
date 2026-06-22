package com.medilabo.notesservice.exception;

public class NoteNotFoundException extends RuntimeException {

    public NoteNotFoundException(String id) {
        super("Note introuvable : " + id);
    }
}
