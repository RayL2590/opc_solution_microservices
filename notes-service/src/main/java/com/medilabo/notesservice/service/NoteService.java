package com.medilabo.notesservice.service;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteDTO addNote(NoteRequest req) {
        Note note = Note.builder()
                .patId(req.getPatId())
                .patient(req.getPatient())
                .note(req.getNote())
                .build();

        Note saved = noteRepository.save(note);
        // Logger patId et id uniquement — le texte de la note est un PII.
        log.info("Note saved id={} patId={}", saved.getId(), saved.getPatId());
        return NoteDTO.from(saved);
    }
}
