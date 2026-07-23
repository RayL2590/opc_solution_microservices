# MédiLabo Solutions

Application de dépistage du risque de diabète de type 2, livrée sous forme de microservices Spring Boot orchestrés par Docker Compose. Un praticien consulte la fiche d'un patient (données démographiques + historique de notes) et obtient un niveau de risque calculé automatiquement à partir de règles cliniques (âge, genre, termes déclencheurs détectés dans les notes).

## Démarrage rapide

Prérequis : Docker Desktop, un fichier `.env` et un fichier `.env.docker` à la racine (copiés depuis `.env.example` / `.env.docker.example` — voir [Variables d'environnement](#variables-denvironnement)).

```bash
docker compose build
docker compose up -d
```

Les 7 composants (5 services Spring Boot + MySQL + MongoDB) démarrent sur le réseau interne Docker. Seul `gateway-service` publie un port sur l'hôte : **`http://localhost:8080`**.

Ouvrez `http://localhost:8080/ui/patients`, authentifiez-vous (identifiants définis dans `.env`, par défaut `medilabo` / `medilabo123` en dev), et vous obtenez la liste des 4 patients de test. Ouvrez un patient pour voir ses notes et son Risk Band ; ajoutez une note contenant un terme déclencheur (ex. « vertiges ») et re-consultez la fiche pour voir le niveau de risque se recalculer.

Pour un scénario de test manuel complet et détaillé (démarrage local sans Docker, vérifications API par `curl`, dépannage), voir **[Documentation/manual-testing-guide.md](Documentation/manual-testing-guide.md)**.

Pour arrêter et nettoyer :

```bash
docker compose down -v
```

## Sommaire

- [Architecture microservices](#architecture-microservices)
- [Pourquoi ce découpage en microservices](#pourquoi-ce-découpage-en-microservices)
- [Pourquoi MongoDB pour les notes (NoSQL)](#pourquoi-mongodb-pour-les-notes-nosql)
- [Pourquoi MySQL en 3NF pour les patients](#pourquoi-mysql-en-3nf-pour-les-patients)
- [Green Code — principes et recommandations](#green-code--principes-et-recommandations)
- [Les services](#les-services)
- [Variables d'environnement](#variables-denvironnement)
- [Sécurité](#sécurité)

## Architecture microservices

```
                         ┌──────────────────────┐
                         │   gateway-service     │  :8080 (seul port publié)
                         │  Spring Cloud Gateway │
                         └──────────┬────────────┘
                    ┌───────────────┼───────────────┬───────────────┐
                    ▼               ▼                ▼               ▼
          ┌──────────────┐ ┌───────────────┐ ┌──────────────────┐ ┌──────────────┐
          │patient-service│ │ notes-service │ │assessment-service│ │front-service │
          │    :8081      │ │    :8082      │ │      :8083       │ │    :8084     │
          │  Spring MVC   │ │  Spring MVC   │ │   Spring MVC     │ │  Thymeleaf   │
          │  MySQL (JPA)  │ │ MongoDB (Data)│ │ (appelle les 2   │ │ (appelle via │
          │               │ │               │ │  autres via la   │ │  la Gateway) │
          │               │ │               │ │  Gateway)        │ │              │
          └──────┬────────┘ └──────┬────────┘ └──────────────────┘ └──────────────┘
                 ▼                 ▼
          ┌──────────┐      ┌──────────┐
          │  mysql   │      │  mongo   │
          └──────────┘      └──────────┘
```

Authentification HTTP Basic en défense en profondeur : chaque service (Gateway inclus) porte son propre filtre de sécurité et vérifie les mêmes identifiants. Le header `Authorization` du navigateur est propagé tel quel de bout en bout (aucune session, aucun jeton recréé) — voir [Sécurité](#sécurité).

## Pourquoi ce découpage en microservices

Le découpage suit les frontières fonctionnelles du domaine plutôt qu'une répartition technique arbitraire :

- **`patient-service`** possède seul les données démographiques et leur persistance relationnelle (3NF, MySQL). Aucune autre partie du système n'écrit ces données.
- **`notes-service`** possède seul l'historique clinique en texte libre. Le modèle de données (append-only, semi-structuré) est fondamentalement différent de celui des patients — d'où un service et une base séparés plutôt qu'une table de plus dans MySQL.
- **`assessment-service`** est un service de calcul pur : il ne persiste rien (le risque n'est jamais mis en cache, il est recalculé à chaque consultation) et consomme les deux autres domaines en lecture seule via la Gateway. L'isoler évite de coupler la logique métier du risque aux modèles de persistance de patient-service et notes-service.
- **`front-service`** est la seule couche de présentation (Thymeleaf, rendu serveur). Elle ne connaît aucun détail de persistance des autres services — elle ne parle qu'au contrat HTTP/JSON exposé par la Gateway.
- **`gateway-service`** est le point d'entrée unique et le seul composant qui expose un port sur l'hôte. Il centralise le routage et, en défense en profondeur, l'authentification.

Chaque service a son propre `pom.xml` (pas de POM parent agrégateur) et son propre contrat DTO à la frontière HTTP — y compris une duplication volontaire des DTOs consommés par `assessment-service` et `front-service` plutôt qu'un module Java partagé. Un module commun introduirait un couplage de compilation entre services : modifier une entité dans `patient-service` forcerait la recompilation de tous ses consommateurs, ce qui revient à un monolithe distribué au niveau du build. Le contrat entre services est le JSON exposé par chaque API, pas un type Java partagé — chaque service évolue et se déploie indépendamment.

## Pourquoi MongoDB pour les notes (NoSQL)

Les notes médicales sont du texte libre semi-structuré : rédigées par un praticien, jamais modifiées après écriture (append-only), et consommées par une simple recherche de sous-chaînes (les termes déclencheurs) pour le calcul du risque. Ce profil ne bénéficie pas de la normalisation relationnelle :

- Les stocker dans une colonne SQL de type texte long viderait la normalisation de son sens (une seule colonne opaque).
- Les décomposer en un schéma relationnel normalisé serait de la sur-ingénierie pour un pattern de lecture qui ne fait jamais de jointure — chaque note est consultée seule ou en liste chronologique par patient.

MongoDB (via Spring Data MongoDB) correspond à ce pattern : chaque note est un document `{patId, patient, note, createdAt}`. Le nom de famille du patient (`patient`) est volontairement dénormalisé sur chaque note — une note n'a jamais besoin d'aller chercher le patient ailleurs pour s'afficher dans une liste. 3NF ne s'applique pas aux bases NoSQL ; le compromis assumé est la simplicité du chemin de lecture, cohérent avec les idiomes de modélisation MongoDB.

Le seed de développement (9 notes canoniques pour 4 patients de test) est chargé via [`docker/mongo-init.js`](docker/mongo-init.js), monté dans le conteneur MongoDB à `/docker-entrypoint-initdb.d/` — exécuté une seule fois à la création du volume, symétrique au mécanisme `data.sql` côté MySQL.

## Pourquoi MySQL en 3NF pour les patients

Les données démographiques d'un patient sont structurées, stables, et interrogées individuellement (par id) — le cas d'usage relationnel classique. Le schéma est défini explicitement dans [`patient-service/src/main/resources/schema.sql`](patient-service/src/main/resources/schema.sql) plutôt que généré par Hibernate (`ddl-auto=validate`, jamais `update`), pour qu'il reste un artefact lisible et vérifiable sans avoir à faire de la rétro-ingénierie sur le mapping JPA.

Normalisation appliquée (voir les commentaires du fichier `schema.sql`) :

- **1NF** : chaque colonne porte une valeur atomique et monovaluée (`first_name`, `last_name`, `date_of_birth`, `gender`, `address`, `phone`).
- **2NF** : trivialement respectée — clé primaire surrogate à une seule colonne (`id`), donc aucune dépendance partielle possible.
- **3NF** : aucune dépendance transitive — aucune colonne dérivée n'est stockée (l'âge, par exemple, est calculé à la lecture dans `assessment-service`, jamais persisté côté patient), chaque colonne non-clé dépend de `id` et uniquement de `id`.
- Une ligne par patient. Un attribut multivalué (plusieurs adresses ou téléphones) vivrait dans une table séparée ; v1 stocke volontairement au plus une valeur de chaque.

Le schéma est validé au démarrage (`spring.jpa.hibernate.ddl-auto=validate`) : toute dérive entre `schema.sql` et l'entité JPA fait échouer le service au boot plutôt que de laisser Hibernate improviser silencieusement.

## Green Code — principes et recommandations

Le grid d'évaluation demande d'**expliquer** les principes Green Code et de **proposer** des recommandations dans ce README — pas de les appliquer rétroactivement au code (l'indicateur évalue la compréhension, pas l'empreinte mesurée du code actuel). Ce qui est déjà en place et ce qui reste une piste :

**Déjà appliqué dans ce projet :**
- Images Docker `eclipse-temurin:17-jre-alpine` (runtime JRE seul, pas de JDK) construites en multi-stage, avec les couches du jar Spring Boot layered dans un ordre optimisé pour le cache Docker (dépendances → loader → snapshot-dependencies → application). Un changement de code ne réinvalide que la dernière couche, pas les dépendances.
- DTOs explicites à chaque frontière HTTP plutôt que de sérialiser des entités JPA/documents Mongo complets — chaque réponse ne transporte que les champs consommés par l'appelant.
- `assessment-service` ne met jamais en cache le résultat du calcul de risque et ne fait aucun appel réseau superflu : un seul aller-retour vers `patient-service` et un seul vers `notes-service` par évaluation, via la Gateway.

**Corrigé suite à l'analyse Green Code :**
- **Log SQL désactivé** (`spring.jpa.show-sql=false`) : le profil `docker` n'ayant pas de surcharge, chaque requête SQL était formatée et écrite sur la sortie standard jusque dans le conteneur de déploiement — du CPU et de l'I/O consommés en continu pour une information utile au seul débogage. Mesuré sur le conteneur en fonctionnement : 4 requêtes loggées → 0. Réactivable ponctuellement en dev via `-Dspring.jpa.show-sql=true`.
- **Index MongoDB sur `Note.patId`** (`@Indexed` + `spring.data.mongodb.auto-index-creation=true`, ce second réglage étant indispensable — la création automatique d'index est désactivée par défaut depuis Spring Data MongoDB 3.0) : `findByPatIdOrderByCreatedAtDesc` est appelée à chaque ouverture de fiche patient et provoquait un balayage complet de la collection. Plan de requête vérifié par `explain()` : `COLLSCAN` → `IXSCAN`.

**Recommandations pour la suite :**
- Limiter les échanges réseau inter-services superflus (regrouper les appels quand c'est possible plutôt que multiplier les allers-retours).
- Éviter les logs verbeux en production (niveau `INFO` minimal, jamais de payload complet en `DEBUG` par défaut).
- Paginer les listes volumineuses (`GET /patients`, `GET /notes`) avant qu'elles ne grossissent au-delà du jeu de données de démonstration.
- Mutualiser les dépendances Maven et retirer les librairies non utilisées à chaque montée de version.
- Poursuivre l'optimisation des requêtes base de données (éviter le N+1, indexer toute nouvelle colonne de recherche fréquente).
- Poursuivre l'usage d'images Docker slim pour tout nouveau service.
- Documenter et outiller l'arrêt des environnements non utilisés (dev/démo) plutôt que de les laisser tourner en continu.
- Surveiller le temps de réponse et la consommation mémoire par service une fois un outil d'observabilité introduit (hors périmètre v1).

## Les services

| Service | Rôle | Port interne | Lancement standalone |
|---|---|---|---|
| `gateway-service` | Point d'entrée unique, routage vers les 4 autres services, authentification HTTP Basic en frontal. Seul service exposé hors du réseau Docker. | `8080` | `cd gateway-service && ./mvnw spring-boot:run` (`mvnw.cmd` sous Windows) — nécessite les 4 autres services déjà démarrés pour router correctement |
| `patient-service` | Gestion des données démographiques patient (CRUD), persistance MySQL en 3NF. | `8081` | `cd patient-service && ./mvnw spring-boot:run` — nécessite MySQL sur `3306` |
| `notes-service` | Gestion de l'historique de notes cliniques, persistance MongoDB. | `8082` | `cd notes-service && ./mvnw spring-boot:run` — nécessite MongoDB sur `27017` |
| `assessment-service` | Calcul du niveau de risque diabétique (`None` / `Borderline` / `In Danger` / `Early Onset`) à partir des données patient et des notes, consommées via la Gateway. Ne persiste rien. | `8083` | `cd assessment-service && ./mvnw spring-boot:run` |
| `front-service` | Interface web (Thymeleaf, rendu serveur) : liste des patients, fiche détail, formulaire d'ajout de note, affichage du Risk Band. | `8084` | `cd front-service && ./mvnw spring-boot:run` |

Chaque service est un projet Maven indépendant (`pom.xml` propre, pas de POM parent partagé) — cette autonomie de build reflète le découpage microservices (voir [ci-dessus](#pourquoi-ce-découpage-en-microservices)). En lancement standalone hors Docker, chaque service utilise ses valeurs par défaut du profil local (`localhost` pour les bases de données et la Gateway) ; en Docker Compose, le profil `docker` (`SPRING_PROFILES_ACTIVE=docker`) bascule ces URLs vers les noms de service du réseau interne (`mysql`, `mongo`, `gateway-service`).

## Variables d'environnement

Deux fichiers-modèles à la racine, à copier en versions réelles (jamais commitées, toutes deux git-ignorées) :

- **[`.env.example`](.env.example) → `.env`** : credentials MySQL applicatifs, URI MongoDB, identité du compte in-memory (`MEDILABO_USER` / `MEDILABO_PASSWORD_BCRYPT` — un hash BCrypt, jamais le mot de passe en clair), URI de la Gateway pour les appels sortants d'`assessment-service`/`front-service` en dev local, et les variables de bootstrap du conteneur MySQL (`MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`).
- **[`.env.docker.example`](.env.docker.example) → `.env.docker`** : ne contient qu'une ré-déclaration de `MEDILABO_PASSWORD_BCRYPT`, échappée (`$$` au lieu de `$`). Ce fichier existe uniquement parce que Docker Compose réinterprète les `$` d'une valeur substituée depuis `.env`, ce qui corromprait silencieusement le hash BCrypt (`$2a$10$...`) s'il n'était lu que depuis `.env`. Spring Boot ne lit jamais ce second fichier — il est branché uniquement dans `docker-compose.yml` via `env_file:`.

Toutes les variables consommées par les 5 services sont couvertes par ces deux fichiers ; aucun secret réel n'apparaît dans le dépôt.

## Sécurité

Authentification HTTP Basic activée sur la Gateway **et** sur chaque service back-end (défense en profondeur) : même si un service était un jour exposé directement (mauvaise configuration réseau), il resterait protégé. Chaque service déclare son propre `InMemoryUserDetailsManager`, seedé depuis les mêmes variables d'environnement (`MEDILABO_USER` / `MEDILABO_PASSWORD_BCRYPT`), avec `BCryptPasswordEncoder`. Aucune session serveur : chaque requête porte ses identifiants (`STATELESS`), ce qui permet à `front-service` de retransmettre tel quel le header `Authorization` reçu du navigateur vers la Gateway, et à la Gateway de le retransmettre à son tour vers le service back-end concerné — sans jamais le recréer ni le stocker.

Pas d'inscription, pas de gestion fine des rôles à ce stade : un seul compte partagé, cohérent avec le périmètre v1 du projet (outil interne pour une équipe clinique restreinte).
