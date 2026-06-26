package com.medilabo.notesservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

// Séparé de NotesServiceApplication pour que @WebMvcTest ne charge pas @EnableMongoAuditing
// (qui requiert mongoMappingContext, absent dans le slice MVC).
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
