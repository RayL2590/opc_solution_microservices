package com.medilabo.notesservice.controller;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
@Slf4j
public class NoteController {

    private final NoteService noteService;

    // Ces logs tracent juste la requête entrante (id seulement, jamais le texte de la note,
    // c'est du PII). En debug parce que c'est le chemin nominal — les échecs sont déjà loggés
    // en warn/error côté GlobalExceptionHandler.
    @PostMapping
    public ResponseEntity<NoteDTO> addNote(@Valid @RequestBody NoteRequest req) {
        log.debug("POST /notes patId={}", req.getPatId());
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.addNote(req));
    }

    @GetMapping
    public ResponseEntity<List<NoteDTO>> getNotesByPatId(@RequestParam Integer patId) {
        log.debug("GET /notes patId={}", patId);
        return ResponseEntity.ok(noteService.getNotesByPatId(patId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDTO> getNoteById(@PathVariable String id) {
        log.debug("GET /notes/{}", id);
        return ResponseEntity.ok(noteService.getNoteById(id));
    }
}
