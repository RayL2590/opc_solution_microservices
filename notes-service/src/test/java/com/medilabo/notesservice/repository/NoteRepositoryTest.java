package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @DataMongoTest tape un Mongo réel localhost:27017 — Mongo doit être démarré avant mvn test.
// Même brittleness que @DataJpaTest sur patient-service ; déférée à Epic 6 (CI / isolation).
@DataMongoTest
class NoteRepositoryTest {

    @Autowired
    private NoteRepository noteRepository;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
    }

    @Test
    void findByPatId_returnsTwoNotes_orderedByCreatedAtDesc() throws InterruptedException {
        // @CreatedDate pose le timestamp réel à l'insert — on insère les deux notes
        // séquentiellement avec 10 ms d'écart pour garantir un ordre observable.
        Note older = Note.builder().patId(2).patient("TestBorderline")
                .note("première note").build();
        noteRepository.save(older);

        Thread.sleep(10);

        Note newer = Note.builder().patId(2).patient("TestBorderline")
                .note("deuxième note").build();
        noteRepository.save(newer);

        List<Note> result = noteRepository.findByPatIdOrderByCreatedAtDesc(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatedAt()).isAfter(result.get(1).getCreatedAt());
        assertThat(result.get(0).getNote()).isEqualTo("deuxième note");
    }

    @Test
    void findByPatId_unknownPatId_returnsEmptyList() {
        List<Note> result = noteRepository.findByPatIdOrderByCreatedAtDesc(99);
        assertThat(result).isEmpty();
    }

    @Test
    void save_setsCreatedAt_whenInserted() {
        Note note = Note.builder().patId(1).patient("TestNone")
                .note("Le patient se sent bien").build();

        Note saved = noteRepository.save(note);

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
