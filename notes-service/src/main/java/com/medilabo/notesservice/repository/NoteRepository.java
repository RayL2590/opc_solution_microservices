package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.model.Note;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NoteRepository extends MongoRepository<Note, String> {

    List<Note> findByPatIdOrderByCreatedAtDesc(Integer patId);
}
