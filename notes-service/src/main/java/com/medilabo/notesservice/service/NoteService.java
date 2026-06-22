package com.medilabo.notesservice.service;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.exception.NoteNotFoundException;
import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<NoteDTO> getNotesByPatId(Integer patId) {
        List<NoteDTO> notes = noteRepository.findByPatIdOrderByCreatedAtDesc(patId)
                .stream()
                .map(NoteDTO::from)
                .toList();
        log.info("Notes fetched patId={} count={}", patId, notes.size());
        return notes;
    }

    public NoteDTO getNoteById(String id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new NoteNotFoundException(id));
        log.info("Note fetched id={} patId={}", note.getId(), note.getPatId());
        return NoteDTO.from(note);
    }
}
