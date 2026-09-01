package com.medilabo.notesservice.repository;

import com.medilabo.notesservice.model.Note;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NoteRepository extends MongoRepository<Note, String> {

    // Le tri secondaire sur id sert à départager les createdAt identiques (même milliseconde).
    // Ça marche parce que l'ObjectId Mongo est monotone par insertion tant qu'on a un seul writer (le cas aujourd'hui) — il encode même son propre timestamp dans les 4 premiers octets.
    List<Note> findByPatIdOrderByCreatedAtDescIdDesc(Integer patId);
}
