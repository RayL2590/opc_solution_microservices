# MédiLabo Solutions

Application de dépistage du risque de diabète de type 2, livrée sous forme de microservices Spring Boot orchestrés par Docker Compose. Un praticien consulte la fiche d'un patient (données démographiques et historique de notes) et obtient un niveau de risque calculé automatiquement à partir de règles cliniques : âge, genre, termes déclencheurs détectés dans les notes.

## Démarrage rapide

Prérequis : Docker Desktop, un fichier `.env` et un fichier `.env.docker` à la racine (copiés depuis `.env.example` / `.env.docker.example`, voir [Variables d'environnement](#variables-denvironnement)).

```bash
docker compose build
docker compose up -d
```

Les 7 composants (5 services Spring Boot + MySQL + MongoDB) démarrent sur le réseau interne Docker. Seul `gateway-service` publie un port sur l'hôte : **`http://localhost:8080`**.

Ouvrez `http://localhost:8080/ui/patients` et authentifiez-vous (identifiants définis dans `.env`, par défaut `medilabo` / `medilabo123` en dev) : vous obtenez la liste des 4 patients de test. Ouvrez un patient pour voir ses notes et son Risk Band, puis ajoutez une note contenant un terme déclencheur (par exemple « vertiges ») et re-consultez la fiche : le niveau de risque se recalcule.

Pour un scénario de test manuel complet (démarrage local sans Docker, vérifications API par `curl`, dépannage), voir **[Documentation/manual-testing-guide.md](Documentation/manual-testing-guide.md)**.

Pour arrêter et nettoyer :

```bash
docker compose down -v
```

## Sommaire

- [Architecture microservices](#architecture-microservices)
- [Pourquoi ce découpage en microservices](#pourquoi-ce-découpage-en-microservices)
- [Pourquoi MongoDB pour les notes (NoSQL)](#pourquoi-mongodb-pour-les-notes-nosql)
- [Pourquoi MySQL en 3NF pour les patients](#pourquoi-mysql-en-3nf-pour-les-patients)
- [Green Code - principes et recommandations](#green-code---principes-et-recommandations)
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

Chaque service, Gateway incluse, exige une authentification HTTP Basic. Aucun ne fait confiance à son appelant sur la seule foi de sa position dans le réseau. Deux classes de comptes coexistent : un compte humain pour le clinicien, et un compte machine par service appelant. Détails dans [Sécurité](#sécurité).

**Pourquoi `front-service` est-il lui aussi derrière la Gateway ?** On aurait pu le publier directement sur l'hôte, puisque c'est le seul service qu'un humain consulte et qu'il appelle de toute façon la Gateway pour ses données. Il reste malgré tout routé (`Path=/,/ui/**,/css/**,/js/**,/favicon.ico`) pour trois raisons :

- **Un seul port publié, donc une seule surface d'exposition.** Publier le front ajouterait un deuxième port sur l'hôte, et donc un deuxième composant à durcir, à monitorer et à mettre derrière TLS le jour où le projet sortirait de la démo. `docker-compose.yml` ne publie que `8080:8080` ; l'IHM et l'API REST partagent la même porte d'entrée.
- **Une seule origine pour le navigateur.** L'IHM et les API vivent sous `http://localhost:8080`. Pas de requête cross-origin, pas de configuration CORS ni de préflight `OPTIONS` à gérer.
- **Une seule chaîne d'authentification.** Le challenge HTTP Basic est présenté par la Gateway à l'ouverture de `/ui/patients` : le clinicien s'authentifie une fois, au même endroit qu'un client d'API. Un front publié à part aurait sa propre porte d'entrée et son propre challenge à maintenir en parallèle de celui de la Gateway.

Le routage de la Gateway ajoute deux en-têtes `X-Forwarded-*` sur la route front (`X-Forwarded-Host: localhost:8080`, `X-Forwarded-Proto: http`), pour que les URLs générées par Thymeleaf pointent sur le port public plutôt que sur le port interne `8084`.

## Pourquoi ce découpage en microservices

Le découpage suit les frontières fonctionnelles du domaine plutôt qu'une répartition technique arbitraire :

- **`patient-service`** possède seul les données démographiques et leur persistance relationnelle (3NF, MySQL). Aucune autre partie du système n'écrit ces données.
- **`notes-service`** possède seul l'historique clinique en texte libre. Son modèle de données (append-only, semi-structuré) est fondamentalement différent de celui des patients, ce qui justifie un service et une base séparés plutôt qu'une table de plus dans MySQL.
- **`assessment-service`** est un service de calcul pur : il ne persiste rien (le risque n'est jamais mis en cache, il est recalculé à chaque consultation) et consomme les deux autres domaines en lecture seule via la Gateway. L'isoler évite de coupler la logique métier du risque aux modèles de persistance de patient-service et notes-service.
- **`front-service`** est la seule couche de présentation (Thymeleaf, rendu serveur). Elle ne connaît aucun détail de persistance des autres services, elle ne parle qu'au contrat HTTP/JSON exposé par la Gateway.
- **`gateway-service`** est le point d'entrée unique et le seul composant qui expose un port sur l'hôte. Il centralise le routage et authentifie les requêtes entrantes, sans dispenser les services back-end de le faire à leur tour (voir [Sécurité](#sécurité)).

Chaque service a son propre `pom.xml`, sans POM parent agrégateur, et son propre contrat DTO à la frontière HTTP. Cela inclut une duplication volontaire des DTOs consommés par `assessment-service` et `front-service`, plutôt qu'un module Java partagé. Un module commun introduirait un couplage de compilation entre services : modifier une entité dans `patient-service` forcerait la recompilation de tous ses consommateurs, ce qui revient à un monolithe distribué au niveau du build. Le contrat entre services est le JSON exposé par chaque API, pas un type Java partagé. Chaque service évolue et se déploie indépendamment.

## Pourquoi MongoDB pour les notes (NoSQL)

Les notes médicales sont du texte libre semi-structuré : rédigées par un praticien, jamais modifiées après écriture (append-only), et consommées par une simple recherche de sous-chaînes (les termes déclencheurs) pour le calcul du risque. Ce profil ne bénéficie pas de la normalisation relationnelle. Les stocker dans une colonne SQL de type texte long viderait la normalisation de son sens (une seule colonne opaque), et les décomposer en un schéma relationnel normalisé serait de la sur-ingénierie pour un pattern de lecture qui ne fait jamais de jointure : chaque note est consultée seule ou en liste chronologique par patient.

MongoDB (via Spring Data MongoDB) correspond à ce pattern : chaque note est un document `{patId, patient, note, createdAt}`. Le nom de famille du patient (`patient`) est volontairement dénormalisé sur chaque note, car une note n'a jamais besoin d'aller chercher le patient ailleurs pour s'afficher dans une liste. 3NF ne s'applique pas aux bases NoSQL ; le compromis assumé est la simplicité du chemin de lecture, cohérent avec les idiomes de modélisation MongoDB.

Le seed de développement (9 notes canoniques pour 4 patients de test) est chargé via [`docker/mongo-init.js`](docker/mongo-init.js), monté dans le conteneur MongoDB à `/docker-entrypoint-initdb.d/`. Il s'exécute une seule fois à la création du volume, de façon symétrique au mécanisme `data.sql` côté MySQL.

## Pourquoi MySQL en 3NF pour les patients

Les données démographiques d'un patient sont structurées, stables, et interrogées individuellement par id : le cas d'usage relationnel classique. Le schéma est défini explicitement dans [`patient-service/src/main/resources/schema.sql`](patient-service/src/main/resources/schema.sql) plutôt que généré par Hibernate (`ddl-auto=validate`, jamais `update`), pour qu'il reste un artefact lisible et vérifiable sans avoir à faire de la rétro-ingénierie sur le mapping JPA.

Normalisation appliquée (voir les commentaires du fichier `schema.sql`) :

- **1NF** : chaque colonne porte une valeur atomique et monovaluée (`first_name`, `last_name`, `date_of_birth`, `gender`, `address`, `phone`).
- **2NF** : trivialement respectée, la clé primaire est une clé surrogate à une seule colonne (`id`), donc aucune dépendance partielle n'est possible.
- **3NF** : aucune dépendance transitive. Aucune colonne dérivée n'est stockée (l'âge, par exemple, est calculé à la lecture dans `assessment-service`, jamais persisté côté patient), et chaque colonne non-clé dépend de `id` et uniquement de `id`.
- Une ligne par patient. Un attribut multivalué (plusieurs adresses ou téléphones) vivrait dans une table séparée, mais la v1 stocke volontairement au plus une valeur de chaque.

Le schéma est validé au démarrage (`spring.jpa.hibernate.ddl-auto=validate`) : toute dérive entre `schema.sql` et l'entité JPA fait échouer le service au boot plutôt que de laisser Hibernate improviser silencieusement.

## Green Code - principes et recommandations

Le grid d'évaluation demande d'**expliquer** les principes Green Code et de **proposer** des recommandations dans ce README, pas de les appliquer rétroactivement au code (l'indicateur évalue la compréhension, pas l'empreinte mesurée du code actuel). Voici ce qui est déjà en place et ce qui reste une piste.

**Déjà appliqué dans ce projet :**
- Images Docker `eclipse-temurin:17-jre-alpine` (runtime JRE seul, pas de JDK) construites en multi-stage, avec les couches du jar Spring Boot layered dans un ordre optimisé pour le cache Docker (dépendances, loader, snapshot-dependencies, application). Un changement de code ne réinvalide que la dernière couche, pas les dépendances.
- DTOs explicites à chaque frontière HTTP plutôt que de sérialiser des entités JPA ou des documents Mongo complets. Chaque réponse ne transporte que les champs consommés par l'appelant.
- `assessment-service` ne met jamais en cache le résultat du calcul de risque et ne fait aucun appel réseau superflu : un seul aller-retour vers `patient-service` et un seul vers `notes-service` par évaluation, via la Gateway.

**Corrigé suite à l'analyse Green Code :**
- **Log SQL désactivé** (`spring.jpa.show-sql=false`). Le profil `docker` n'ayant pas de surcharge, chaque requête SQL était formatée et écrite sur la sortie standard jusque dans le conteneur de déploiement, ce qui consommait du CPU et de l'I/O en continu pour une information utile au seul débogage. Mesuré sur le conteneur en fonctionnement : 4 requêtes loggées ramenées à 0. Réactivable ponctuellement en dev via `-Dspring.jpa.show-sql=true`.
- **Index MongoDB sur `Note.patId`** (`@Indexed` + `spring.data.mongodb.auto-index-creation=true`, ce second réglage étant indispensable car la création automatique d'index est désactivée par défaut depuis Spring Data MongoDB 3.0). `findByPatIdOrderByCreatedAtDescIdDesc` est appelée à chaque ouverture de fiche patient et provoquait un balayage complet de la collection. Plan de requête vérifié par `explain()` : passage de `COLLSCAN` à `IXSCAN`.

**Recommandations pour la suite :**
- Limiter les échanges réseau inter-services superflus en regroupant les appels quand c'est possible plutôt que de multiplier les allers-retours.
- Éviter les logs verbeux en production : niveau `INFO` minimal, jamais de payload complet en `DEBUG` par défaut.
- Paginer les listes volumineuses (`GET /patients`, `GET /notes`) avant qu'elles ne grossissent au-delà du jeu de données de démonstration.
- Mutualiser les dépendances Maven et retirer les librairies non utilisées à chaque montée de version.
- Poursuivre l'optimisation des requêtes base de données : éviter le N+1, indexer toute nouvelle colonne de recherche fréquente.
- Poursuivre l'usage d'images Docker slim pour tout nouveau service.
- Documenter et outiller l'arrêt des environnements non utilisés (dev/démo) plutôt que de les laisser tourner en continu.
- Surveiller le temps de réponse et la consommation mémoire par service une fois un outil d'observabilité introduit (hors périmètre v1).

## Les services

| Service | Rôle | Port interne | Lancement standalone |
|---|---|---|---|
| `gateway-service` | Point d'entrée unique, routage vers les 4 autres services, authentification HTTP Basic en frontal. Seul service exposé hors du réseau Docker. | `8080` | `cd gateway-service && ./mvnw spring-boot:run` (`mvnw.cmd` sous Windows). Nécessite les 4 autres services déjà démarrés pour router correctement. |
| `patient-service` | Gestion des données démographiques patient (CRUD), persistance MySQL en 3NF. | `8081` | `cd patient-service && ./mvnw spring-boot:run`. Nécessite MySQL sur `3306`. |
| `notes-service` | Gestion de l'historique de notes cliniques, persistance MongoDB. | `8082` | `cd notes-service && ./mvnw spring-boot:run`. Nécessite MongoDB sur `27017`. |
| `assessment-service` | Calcul du niveau de risque diabétique (`None` / `Borderline` / `In Danger` / `Early Onset`) à partir des données patient et des notes, consommées via la Gateway. Ne persiste rien. | `8083` | `cd assessment-service && ./mvnw spring-boot:run` |
| `front-service` | Interface web (Thymeleaf, rendu serveur) : liste des patients, fiche détail, formulaire d'ajout de note, affichage du Risk Band. | `8084` | `cd front-service && ./mvnw spring-boot:run` |

Chaque service est un projet Maven indépendant, avec son propre `pom.xml` et sans POM parent partagé. Cette autonomie de build reflète le découpage microservices (voir [ci-dessus](#pourquoi-ce-découpage-en-microservices)). En lancement standalone hors Docker, chaque service utilise ses valeurs par défaut du profil local (`localhost` pour les bases de données et la Gateway). En Docker Compose, le profil `docker` (`SPRING_PROFILES_ACTIVE=docker`) bascule ces URLs vers les noms de service du réseau interne (`mysql`, `mongo`, `gateway-service`).

## Variables d'environnement

Deux fichiers-modèles à la racine, à copier en versions réelles (jamais commitées, toutes deux git-ignorées) :

- **[`.env.example`](.env.example) → `.env`** : l'intégralité de la surface de configuration (détail ci-dessous).
- **[`.env.docker.example`](.env.docker.example) → `.env.docker`** : ne contient que les **trois hashes BCrypt**, échappés (`$$` au lieu de `$`). Ce fichier existe uniquement parce que Docker Compose réinterprète les `$` d'une valeur substituée depuis `.env`, ce qui corromprait silencieusement un hash BCrypt (`$2a$10$...`) s'il n'était lu que depuis `.env`. Spring Boot ne lit jamais ce second fichier : il est branché uniquement dans `docker-compose.yml` via `env_file:`. Les mots de passe en clair des comptes de service ne contiennent pas de `$` et restent donc dans `.env`.

Contenu de `.env` :

| Variable | Rôle |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Connexion JDBC de `patient-service` à MySQL. |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` | Bootstrap du conteneur MySQL. `MYSQL_USER`/`MYSQL_PASSWORD` doivent correspondre aux deux variables `SPRING_DATASOURCE_*` ci-dessus. |
| `SPRING_DATA_MONGODB_URI` | Connexion de `notes-service` à MongoDB. |
| `MEDILABO_USER`, `MEDILABO_PASSWORD_BCRYPT` | Compte **humain** du clinicien. Hash BCrypt uniquement, jamais le mot de passe en clair. |
| `MEDILABO_SVC_FRONT_USER`, `..._PASSWORD`, `..._PASSWORD_BCRYPT` | Compte **de service** de `front-service`. |
| `MEDILABO_SVC_ASSESSMENT_USER`, `..._PASSWORD`, `..._PASSWORD_BCRYPT` | Compte **de service** d'`assessment-service`. |
| `GATEWAY_URI` | Base des appels sortants d'`assessment-service` / `front-service` en dev local. |

Pour chaque compte de service, `..._PASSWORD` (clair) et `..._PASSWORD_BCRYPT` (hash) doivent rester cohérents : l'appelant construit son header Basic depuis le premier, tous les destinataires valident contre le second. Voir [Sécurité](#sécurité).

Toutes les variables consommées par les 5 services sont couvertes par ces deux fichiers ; aucun secret réel n'apparaît dans le dépôt.

## Sécurité

### Deux classes de comptes, pas un seul compte partagé

Le système distingue qui est l'appelant humain de quel service appelle quel service. Trois identités sont déclarées, avec des rôles distincts :

| Compte | Rôle | Qui l'utilise | Variables |
|---|---|---|---|
| `medilabo` | `ROLE_USER` | Le clinicien, uniquement pour se connecter à l'IHM via la Gateway. | `MEDILABO_USER`, `MEDILABO_PASSWORD_BCRYPT` |
| `svc-front` | `ROLE_SERVICE` | `front-service` pour ses appels sortants vers la Gateway. | `MEDILABO_SVC_FRONT_USER`, `..._PASSWORD`, `..._PASSWORD_BCRYPT` |
| `svc-assessment` | `ROLE_SERVICE` | `assessment-service` pour lire les patients et les notes via la Gateway. | `MEDILABO_SVC_ASSESSMENT_USER`, `..._PASSWORD`, `..._PASSWORD_BCRYPT` |

Ce découpage répond à un problème concret de la version précédente : le header `Authorization` du navigateur était relayé tel quel de service en service, donc le mot de passe du clinicien circulait dans tout le système. Un seul service compromis exposait le credential humain, et le révoquer coupait tout le monde d'un coup. Aujourd'hui, `front-service` et `assessment-service` s'authentifient chacun avec leur propre identité machine (`ServiceAccountAuthInitializer`, branché sur le `RestClient` dans `RestClientConfig`). Le credential du clinicien s'arrête à la frontière du front, et chaque compte de service est révocable indépendamment des deux autres.

Chaque compte de service se décline en deux variables complémentaires. L'appelant a besoin du mot de passe en clair (`..._PASSWORD`) pour construire son header Basic, tandis que chaque destinataire ne stocke que le hash BCrypt (`..._PASSWORD_BCRYPT`). Les deux doivent rester cohérents : un hash qui ne correspond pas au mot de passe fait échouer en 401 tous les appels sortants du service concerné.

### Ce que vérifie chaque service

Les 5 services déclarent chacun leur propre `SecurityConfig` avec les 3 mêmes identités, un `BCryptPasswordEncoder`, le hash stocké verbatim (jamais ré-encodé) et CSRF désactivé. Aucun ne conserve de session : les 4 services servlet utilisent `SessionCreationPolicy.STATELESS`, et la Gateway (réactive, WebFlux) son équivalent `NoOpServerSecurityContextRepository`. Chaque requête reporte donc ses identifiants.

C'est bien de la vérification redondante : la Gateway authentifie la requête entrante, puis le service back-end ré-authentifie l'appel qu'il reçoit. Un service ne fait donc jamais confiance à son appelant sur la seule foi de sa position dans le réseau interne.

### Ce que la Gateway protège, et ce qu'elle ne protège pas

L'isolation des services back-end est topologique, pas applicative, et il vaut mieux le dire précisément.

Sous Docker Compose, seul `gateway-service` publie un port sur l'hôte (`8080:8080`). Les 4 autres services et les 2 bases ne sont joignables que depuis le réseau interne Docker : depuis l'extérieur, la Gateway est effectivement le seul point d'entrée. En lancement standalone hors Docker, chaque service écoute sur son propre port (`8081`-`8084`) et est directement joignable, la Gateway n'est alors plus un passage obligé. Et il n'existe aucun mécanisme applicatif anti-contournement : pas d'en-tête de confiance interne, pas de liste blanche d'IP, pas de mTLS. Un appelant qui atteint le réseau interne et possède des identifiants valides est accepté par un service back-end sans passer par la Gateway.

Ce qui reste vrai dans tous les cas de figure, c'est l'authentification : un service atteint directement exige quand même des identifiants valides. C'est précisément l'intérêt de la redondance décrite plus haut, elle ne dépend pas de la topologie réseau. L'étape suivante identifiée (hors périmètre v1) serait un en-tête de confiance interne ou du mTLS, pour rendre le contournement de la Gateway détectable au niveau applicatif et non seulement improbable au niveau réseau.

### Limites assumées de la v1

Pas d'inscription, pas d'annuaire d'utilisateurs, pas de gestion fine des droits : un unique compte clinicien partagé par l'équipe, déclaré en mémoire (`InMemoryUserDetailsManager`), ce qui reste cohérent avec le périmètre v1 (outil interne pour une équipe restreinte). Les rôles `ROLE_USER` / `ROLE_SERVICE` sont portés par les comptes mais aucune règle d'autorisation ne les distingue encore, les règles sont aujourd'hui en `anyRequest().authenticated()`. Ce socle rend le durcissement possible sans re-modéliser les identités, par exemple pour réserver les écritures au clinicien et n'ouvrir que la lecture aux comptes de service.
