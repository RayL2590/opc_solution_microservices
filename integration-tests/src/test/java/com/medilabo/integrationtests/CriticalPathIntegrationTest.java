package com.medilabo.integrationtests;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que le découpage en microservices marche vraiment de bout en bout en HTTP réel,
 * pas juste service par service isolément (toutes les autres classes de test du projet sont
 * des tranches {@code @WebMvcTest} avec la couche métier mockée).
 *
 * <p><b>Scénario</b> : un {@code GET /assessments/4} envoyé sur le port propre de la Gateway doit
 * renvoyer la tranche de risque "Early Onset" pour le patient 4 de référence ("TestEarlyOnset"),
 * ce qui suppose vraiment d'avoir fait transiter :
 * <ol>
 *   <li>Gateway → assessment-service (route {@code /assessments/**})</li>
 *   <li>assessment-service → Gateway → patient-service (route {@code /patients/**})</li>
 *   <li>assessment-service → Gateway → notes-service (route {@code /notes/**})</li>
 * </ol>
 * Un deuxième scénario va un cran plus loin — {@code GET /ui/patients/4} sur le port propre du
 * front-service — pour prouver que la page rendue côté serveur affiche des données ayant
 * réellement traversé la même chaîne (front-service → Gateway → backends), et pas seulement que
 * les backends sont d'accord entre eux.
 *
 * <p>Aucune frontière entre services n'est mockée. Chaque service tourne comme un vrai
 * **processus JVM enfant** (son propre {@code -exec.jar}, lancé via {@link ProcessBuilder}),
 * en écoute sur un port TCP réel réservé à l'avance — la même logique que
 * {@code docker-compose.yml}, simplement sans Docker pour les cinq services Spring eux-mêmes.
 * patient-service et notes-service pointent vers des {@code mysql:8.0}/{@code mongo:7.0} gérés
 * par Testcontainers — pas besoin de démarrer une base locale à la main, juste un daemon Docker
 * qui tourne. L'authentification utilise les vrais comptes de service par appelant, lus depuis
 * le {@code .env} du dépôt (chaque service en a de toute façon besoin pour tourner seul en
 * {@code mvn test}/local).
 *
 * <p><b>Pourquoi des processus séparés plutôt que des contextes dans la même JVM.</b> Une
 * version antérieure de ce test essayait de démarrer les quatre contextes
 * {@code @SpringBootApplication} dans la JVM de ce module lui-même ({@code SpringApplicationBuilder}
 * + {@code server.port=0}). Deux collisions de classpath indépendantes et sans lien rendaient
 * cette approche impraticable :
 * <ol>
 *   <li>Spring Cloud Gateway embarque {@code GatewayClassPathWarningAutoConfiguration}, qui
 *       refuse de démarrer *tout* contexte voyant à la fois {@code spring-webmvc} et Gateway
 *       sur le classpath — un contrôle {@code @ConditionalOnClass} strict, sans rapport avec
 *       le type d'application web réellement configuré pour ce contexte-là.</li>
 *   <li>Les quatre jars de service embarquent chacun une ressource au même chemin de classpath
 *       ({@code /application.properties}, ou {@code /application.yml} pour la Gateway). Avec les
 *       quatre jars sur un seul classpath de test partagé, le classloader ne résout que la
 *       première correspondance et masque silencieusement les trois autres — les propriétés
 *       {@code medilabo.*} propres à assessment-service ne se chargent jamais, seul le fichier
 *       du voisin qui a gagné la course est pris.</li>
 * </ol>
 * Les deux problèmes viennent du fait de partager un seul classloader JVM entre des applications
 * Spring Boot construites indépendamment ; impossible à corriger avec juste
 * {@code spring.autoconfigure.exclude} ou des surcharges de propriétés. De vrais processus OS
 * (cette version) évitent les deux problèmes : chacun a son propre classpath, exactement comme
 * {@code docker-compose.yml} les fait déjà tourner.
 *
 * <p>Ce test ne vit dans aucun des cinq modules de service : le projet n'a pas de POM agrégateur
 * racine (choix volontaire — chaque service se construit seul), donc il vit dans ce module
 * séparé {@code integration-tests}. Prérequis : faire un {@code mvn install} des cinq services
 * voisins d'abord (ça produit le {@code -exec.jar} que le {@code pom.xml} de chaque service
 * attache maintenant via un classifier Maven, justement pour que le jar simple et l'exécutable
 * coexistent dans le repo local) — lancer {@code mvn install -DskipTests} dans chacun de
 * {@code gateway-service}, {@code patient-service}, {@code notes-service},
 * {@code assessment-service} et {@code front-service}, puis {@code mvn test} dans
 * {@code integration-tests}.
 */
@Testcontainers
class CriticalPathIntegrationTest {

    private static final int CANONICAL_PATIENT_ID = 4;
    private static final String EXPECTED_RISK_BAND = "Early Onset";
    private static final Duration BOOT_TIMEOUT = Duration.ofSeconds(90);

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.0");

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    private static final List<Process> STARTED_PROCESSES = new ArrayList<>();

    private static int gatewayPort;
    private static int frontPort;

    @BeforeAll
    static void startAllServices() throws IOException, InterruptedException {
        int patientPort = reserveFreePort();
        int notesPort = reserveFreePort();
        int assessmentPort = reserveFreePort();
        gatewayPort = reserveFreePort();
        frontPort = reserveFreePort();

        startService("patient-service", patientPort, Map.of(
                "server.port", String.valueOf(patientPort),
                "spring.datasource.url", mysql.getJdbcUrl(),
                "spring.datasource.username", mysql.getUsername(),
                "spring.datasource.password", mysql.getPassword()
        ));

        seedCanonicalNotes();

        startService("notes-service", notesPort, Map.of(
                "server.port", String.valueOf(notesPort),
                "spring.mongodb.uri", mongo.getConnectionString() + "/notesdb"
        ));

        startService("assessment-service", assessmentPort, Map.of(
                "server.port", String.valueOf(assessmentPort),
                "medilabo.gateway.base-url", "http://localhost:" + gatewayPort
        ));

        startService("gateway-service", gatewayPort, Map.of(
                "server.port", String.valueOf(gatewayPort),
                "spring.cloud.gateway.server.webflux.routes[0].id", "patients",
                "spring.cloud.gateway.server.webflux.routes[0].uri", "http://localhost:" + patientPort,
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]", "Path=/patients/**",
                "spring.cloud.gateway.server.webflux.routes[1].id", "notes",
                "spring.cloud.gateway.server.webflux.routes[1].uri", "http://localhost:" + notesPort,
                "spring.cloud.gateway.server.webflux.routes[1].predicates[0]", "Path=/notes/**",
                "spring.cloud.gateway.server.webflux.routes[2].id", "assessments",
                "spring.cloud.gateway.server.webflux.routes[2].uri", "http://localhost:" + assessmentPort,
                "spring.cloud.gateway.server.webflux.routes[2].predicates[0]", "Path=/assessments/**"
        ));

        startService("front-service", frontPort, Map.of(
                "server.port", String.valueOf(frontPort),
                "medilabo.gateway.base-url", "http://localhost:" + gatewayPort
        ));
    }

    /** Insère directement via le driver Mongo les quatre notes de référence du patient 4
     * ("TestEarlyOnset") — le même texte et les mêmes horodatages {@code createdAt} que dans
     * {@code docker/mongo-init.js}, qui est un script d'entrypoint Docker (pas appliqué
     * automatiquement par Spring sur un Mongo Testcontainers nu, contrairement au
     * {@code data.sql} de patient-service que Spring exécute lui-même via
     * {@code spring.sql.init.mode=always}). Ces quatre notes sont exactement celles que
     * {@code RiskCalculatorTest}/{@code AssessmentServiceTest} vérifient déjà comme produisant
     * 7 déclencheurs / "Early Onset". */
    private static void seedCanonicalNotes() {
        try (MongoClient client = MongoClients.create(mongo.getConnectionString())) {
            var notes = client.getDatabase("notesdb").getCollection("note");
            notes.insertOne(new Document()
                    .append("patId", 4)
                    .append("patient", "TestEarlyOnset")
                    .append("note", "Le patient déclare qu'il lui est devenu difficile de monter "
                            + "les escaliers Il se plaint également d'être essoufflé Tests de "
                            + "laboratoire indiquant que les anticorps sont élevés Réaction aux médicaments")
                    .append("createdAt", java.util.Date.from(java.time.Instant.parse("2024-01-10T09:00:00Z"))));
            notes.insertOne(new Document()
                    .append("patId", 4)
                    .append("patient", "TestEarlyOnset")
                    .append("note", "Le patient déclare qu'il a mal au dos lorsqu'il reste assis "
                            + "pendant longtemps")
                    .append("createdAt", java.util.Date.from(java.time.Instant.parse("2024-01-10T10:00:00Z"))));
            notes.insertOne(new Document()
                    .append("patId", 4)
                    .append("patient", "TestEarlyOnset")
                    .append("note", "Le patient déclare avoir commencé à fumer depuis peu "
                            + "Hémoglobine A1C supérieure au niveau recommandé")
                    .append("createdAt", java.util.Date.from(java.time.Instant.parse("2024-01-10T11:00:00Z"))));
            notes.insertOne(new Document()
                    .append("patId", 4)
                    .append("patient", "TestEarlyOnset")
                    .append("note", "Taille, Poids, Cholestérol, Vertige et Réaction")
                    .append("createdAt", java.util.Date.from(java.time.Instant.parse("2024-01-10T12:00:00Z"))));
        }
    }

    /** Lance {@code <module>-0.0.1-SNAPSHOT-exec.jar} depuis le repo Maven local comme un vrai
     * processus enfant (stdout/stderr redirigés vers {@code target/<module>-boot.log}, référencé
     * dans tout message d'échec levé par cette méthode), puis sonde le port donné jusqu'à ce
     * qu'il accepte une connexion TCP ou que {@link #BOOT_TIMEOUT} soit écoulé. */
    private static void startService(String artifactId, int port, Map<String, String> properties)
            throws IOException, InterruptedException {
        Path jar = Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "medilabo",
                artifactId, "0.0.1-SNAPSHOT", artifactId + "-0.0.1-SNAPSHOT-exec.jar");
        if (!jar.toFile().exists()) {
            throw new IllegalStateException(jar + " est introuvable"
                    + " — lance d'abord `mvn install -DskipTests` dans " + artifactId + ".");
        }

        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + "/bin/java");
        command.add("-jar");
        command.add(jar.toString());
        properties.forEach((key, value) -> command.add("--" + key + "=" + value));

        // On écrit dans un fichier plutôt que d'hériter du stdout de cette JVM : écrire directement
        // dans le flux console capturé par surefire depuis plusieurs processus enfants concurrents
        // corrompt son canal (observé sous forme d'un avertissement surefire "Corrupted channel").
        // Le log de chaque service reste consultable en cas d'échec via bootLogFile ci-dessous.
        Path bootLogFile = Path.of("target", artifactId + "-boot.log").toAbsolutePath();
        bootLogFile.getParent().toFile().mkdirs();
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(bootLogFile.toFile());
        // Chaque service lit son propre .env à la racine du dépôt via
        // `spring.config.import=optional:file:../.env[.properties]` — un chemin relatif résolu
        // depuis le répertoire de travail du processus, donc il doit être lancé depuis le
        // répertoire du module de ce service (comme un développeur qui lance `mvn spring-boot:run`
        // depuis l'intérieur de chaque module aujourd'hui).
        builder.directory(Path.of("..", artifactId).toAbsolutePath().normalize().toFile());
        Process process = builder.start();
        STARTED_PROCESSES.add(process);

        try {
            waitForPort(artifactId, port);
        } catch (IllegalStateException timedOut) {
            throw new IllegalStateException(timedOut.getMessage() + " — voir " + bootLogFile, timedOut);
        }
        if (!process.isAlive()) {
            throw new IllegalStateException(artifactId + " s'est arrêté avant d'ouvrir son port (code de sortie "
                    + process.exitValue() + ") — voir " + bootLogFile);
        }
    }

    private static void waitForPort(String artifactId, int port) throws InterruptedException {
        Instant deadline = Instant.now().plus(BOOT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            try (var socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress("localhost", port), 500);
                return;
            } catch (IOException notUpYet) {
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException(artifactId + " n'a pas ouvert le port " + port
                + " dans le délai de " + BOOT_TIMEOUT);
    }

    private static int reserveFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @AfterAll
    static void stopAllServices() {
        // L'ordre inverse (Gateway en premier) n'est pas requis pour que ce soit correct — chaque
        // processus est indépendant — mais évite un instant où la Gateway tourne encore sans aucun
        // backend vivant derrière.
        for (int i = STARTED_PROCESSES.size() - 1; i >= 0; i--) {
            STARTED_PROCESSES.get(i).destroy();
        }
        for (Process process : STARTED_PROCESSES) {
            try {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    /**
     * Exécuté deux fois (critère d'acceptation : déterministe à la répétition, pas d'état résiduel)
     * — chaque exécution renvoie le même GET vers les processus partagés déjà démarrés ; aucun
     * test ne modifie les données du patient 4.
     */
    @RepeatedTest(2)
    void criticalPath_gatewayToRiskAssessment_returnsExpectedRiskBand() {
        TestRestTemplate rest = new TestRestTemplate("medilabo", "medilabo123");

        ResponseEntity<Map> response = rest.getForEntity(
                "http://localhost:" + gatewayPort + "/assessments/" + CANONICAL_PATIENT_ID,
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("patId")).isEqualTo(CANONICAL_PATIENT_ID);
        assertThat(response.getBody().get("riskBand")).isEqualTo(EXPECTED_RISK_BAND);
        assertThat(response.getBody().get("triggerCount")).isEqualTo(7);

        Map<?, ?> patient = (Map<?, ?>) response.getBody().get("patient");
        assertThat(patient.get("lastName")).isEqualTo("TestEarlyOnset");
    }

    /**
     * Même chaîne, un cran plus loin : la requête entre par front-service (pas par la Gateway),
     * donc la page rendue ne peut contenir le nom du patient et la tranche de risque que si
     * front-service a vraiment appelé la Gateway, qui a elle-même appelé patient-service,
     * notes-service et assessment-service — front-service n'a aucune donnée à lui. On vérifie le
     * HTML rendu côté serveur car c'est le vrai contrat de front-service ; un backend mocké
     * afficherait à la place le balisage de secours.
     */
    @Test
    void criticalPath_frontServiceDetailPage_rendersLiveUpstreamData() {
        TestRestTemplate rest = new TestRestTemplate("medilabo", "medilabo123");

        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + frontPort + "/ui/patients/" + CANONICAL_PATIENT_ID,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("TestEarlyOnset")
                .contains(EXPECTED_RISK_BAND)
                .contains("Historique des notes")
                // Une des notes insérées pour le patient 4 : prouve que le passage par notes-service
                // arrive bien jusqu'à la page aussi.
                .contains("difficile de monter");
    }

    @Test
    void frontServiceDetailPage_withoutCredentials_isRejected() {
        TestRestTemplate rest = new TestRestTemplate();

        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + frontPort + "/ui/patients/" + CANONICAL_PATIENT_ID,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
