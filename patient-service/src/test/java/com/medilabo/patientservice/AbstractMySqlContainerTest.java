package com.medilabo.patientservice;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

// Base partagée : un seul point pour faire évoluer le tag d'image (mysql:8.0, aligné sur docker-compose.yml).
@Testcontainers
public abstract class AbstractMySqlContainerTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");
}
