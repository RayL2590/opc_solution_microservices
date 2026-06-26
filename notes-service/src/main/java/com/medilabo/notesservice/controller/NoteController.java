package com.medilabo.notesservice.controller;

import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.dto.NoteRequest;
import com.medilabo.notesservice.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<NoteDTO> addNote(@Valid @RequestBody NoteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.addNote(req));
    }

    @GetMapping
    public ResponseEntity<List<NoteDTO>> getNotesByPatId(@RequestParam Integer patId) {
        return ResponseEntity.ok(noteService.getNotesByPatId(patId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDTO> getNoteById(@PathVariable String id) {
        return ResponseEntity.ok(noteService.getNoteById(id));
    }
}
