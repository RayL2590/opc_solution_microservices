package com.medilabo.notesservice.service;

import com.medilabo.notesservice.AbstractMongoContainerTest;
import com.medilabo.notesservice.config.MongoConfig;
import com.medilabo.notesservice.dto.NoteDTO;
import com.medilabo.notesservice.exception.NoteNotFoundException;
import com.medilabo.notesservice.model.Note;
import com.medilabo.notesservice.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// NoteService réel branché sur un vrai NoteRepository (Mongo Testcontainers), pour tester la
// logique du service - mapping NoteDTO, ordre, NoteNotFoundException - contre des données
// vraiment persistées. NoteControllerTest de son côté mocke le service, donc ne couvre pas ça.
@DataMongoTest
@Import({MongoConfig.class, NoteService.class})
class NoteServiceIntegrationTest extends AbstractMongoContainerTest {

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteRepository noteRepository;

    @BeforeEach
    void setUp() {
        noteRepository.deleteAll();
    }

    @Test
    void getNotesByPatId_returnsNotesMostRecentFirst() {
        Note older = noteRepository.save(Note.builder().patId(2).patient("TestBorderline")
                .note("première note").build());
        Note newer = noteRepository.save(Note.builder().patId(2).patient("TestBorderline")
                .note("deuxième note").build());

        List<NoteDTO> result = noteService.getNotesByPatId(2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).note()).isEqualTo("deuxième note");
        assertThat(result.get(0).id()).isEqualTo(newer.getId());
        assertThat(result.get(1).note()).isEqualTo("première note");
        assertThat(result.get(1).id()).isEqualTo(older.getId());
    }

    @Test
    void getNotesByPatId_mapsEveryPersistedField() {
        noteRepository.save(Note.builder().patId(2).patient("TestBorderline")
                .note("première note").build());

        NoteDTO dto = noteService.getNotesByPatId(2).get(0);

        assertThat(dto.patId()).isEqualTo(2);
        assertThat(dto.patient()).isEqualTo("TestBorderline");
        assertThat(dto.note()).isEqualTo("première note");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void getNotesByPatId_unknownPatId_returnsEmptyList() {
        noteRepository.save(Note.builder().patId(2).patient("TestBorderline")
                .note("première note").build());

        assertThat(noteService.getNotesByPatId(99)).isEmpty();
    }

    @Test
    void getNoteById_knownId_returnsNote() {
        Note saved = noteRepository.save(Note.builder().patId(1).patient("TestNone")
                .note("Le patient se sent bien").build());

        NoteDTO result = noteService.getNoteById(saved.getId());

        assertThat(result.id()).isEqualTo(saved.getId());
        assertThat(result.patId()).isEqualTo(1);
        assertThat(result.patient()).isEqualTo("TestNone");
        assertThat(result.note()).isEqualTo("Le patient se sent bien");
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void getNoteById_unknownId_throwsNoteNotFoundException() {
        assertThatThrownBy(() -> noteService.getNoteById("507f1f77bcf86cd799439011"))
                .isInstanceOf(NoteNotFoundException.class)
                .hasMessageContaining("507f1f77bcf86cd799439011");
    }
}
