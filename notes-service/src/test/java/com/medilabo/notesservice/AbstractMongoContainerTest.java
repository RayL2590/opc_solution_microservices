package com.medilabo.notesservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

// Base partagée : un seul point pour faire évoluer le tag d'image (mongo:7.0, aligné sur docker-compose.yml).
@Testcontainers
public abstract class AbstractMongoContainerTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");
}
