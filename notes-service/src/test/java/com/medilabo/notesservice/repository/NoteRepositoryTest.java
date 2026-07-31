package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.AbstractMongoContainerTest;
import com.medilabo.notesservice.config.MongoConfig;
import com.medilabo.notesservice.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @EnableMongoAuditing est dans MongoConfig, pas ici, sinon @WebMvcTest chargerait le contexte Mongo.
@DataMongoTest
@Import(MongoConfig.class)
class NoteRepositoryTest extends AbstractMongoContainerTest {

    @Autowired
    private NoteRepository noteRepository;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
    }

    @Test
    void findByPatId_returnsTwoNotes_orderedByCreatedAtDesc() {
        // Pas besoin d'attendre entre les deux save() : le tri secondaire sur id (ObjectId,
        // monotone par insertion) départage un createdAt identique même à la milliseconde près.
        Note older = Note.builder().patId(2).patient("TestBorderline")
                .note("première note").build();
        noteRepository.save(older);

        Note newer = Note.builder().patId(2).patient("TestBorderline")
                .note("deuxième note").build();
        noteRepository.save(newer);

        List<Note> result = noteRepository.findByPatIdOrderByCreatedAtDescIdDesc(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNote()).isEqualTo("deuxième note");
        assertThat(result.get(1).getNote()).isEqualTo("première note");
    }

    @Test
    void findByPatId_unknownPatId_returnsEmptyList() {
        List<Note> result = noteRepository.findByPatIdOrderByCreatedAtDescIdDesc(99);
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
