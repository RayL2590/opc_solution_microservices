# MédiLabo Solutions

Monorepo Spring Boot du projet **MédiLabo Solutions**, une application de dépistage du risque de diabète de type 2 basée sur une architecture microservices.

## Objectif du projet

L'application permet de :
- gérer les informations démographiques des patients ;
- enregistrer des notes médicales de suivi ;
- calculer un niveau de risque diabétique (`None`, `Borderline`, `In Danger`, `Early onset`) ;
- exposer les services via une gateway sécurisée ;
- proposer une interface web simple pour les utilisateurs métier.

## Architecture

Le dépôt contient 5 microservices Spring Boot :

| Service | Rôle | Stack principale |
|---|---|---|
| `patient-service` | Gestion des patients | Spring Boot, Spring Web, Spring Data JPA, MySQL, Spring Security, Validation, Lombok |
| `notes-service` | Gestion des notes médicales | Spring Boot, Spring Web, Spring Data MongoDB, Spring Security, Validation, Lombok |
| `assessment-service` | Calcul du risque diabétique | Spring Boot, Spring Web, Spring Security, Lombok |
| `front-service` | Interface web | Spring Boot, Thymeleaf, Spring Security, Lombok |
| `gateway-service` | Point d’entrée unique | Spring Cloud Gateway, WebFlux, Spring Security |

## Arborescence

```text
opc_solution_microservices/
├── assessment-service/
├── Documentation/
├── front-service/
├── gateway-service/
├── notes-service/
├── patient-service/
└── README.md
```

## Documentation fonctionnelle

Le dossier `Documentation/` contient les éléments de cadrage du projet :
- exigences produit ;
- résumé des besoins fonctionnels ;
- scénario projet ;
- recommandations générales.

## État actuel

Le monorepo est structuré pour accueillir l’ensemble des sprints du projet.
À ce stade, `patient-service` constitue la première base fonctionnelle du domaine patient.
Les autres services sont présents pour mettre en place l’architecture cible et seront enrichis au fil des sprints.

## Prérequis

- Java 17
- Maven Wrapper (`mvnw` / `mvnw.cmd`)
- MySQL pour `patient-service`
- MongoDB pour `notes-service`
- Git
- Docker Desktop (prévu pour la phase conteneurisation)

## Lancement en local

Chaque microservice est autonome et possède son propre `pom.xml`.

### Exemple : démarrer `patient-service`

Sous Windows :

```bash
cd patient-service
mvnw.cmd spring-boot:run
```

Sous macOS / Linux :

```bash
cd patient-service
./mvnw spring-boot:run
```

Le service patient est configuré sur le port `8081`.

## Sécurité

Le projet utilise Spring Security pour protéger l’accès aux données sensibles.
Le choix actuel est volontairement simple pour le cadrage initial :
- authentification HTTP Basic ;
- utilisateur en mémoire ;
- sécurisation des endpoints ;
- pas d’inscription ni de gestion fine des rôles à ce stade.

## Données métier attendues

### Sprint 1
- consultation des patients ;
- création d’un patient ;
- mise à jour d’un patient.

### Sprint 2
- consultation de l’historique médical ;
- ajout de notes patient.

### Sprint 3
- calcul du niveau de risque diabétique à partir des données patient et des notes.

## Docker

Un fichier `docker-compose.yml` est prévu à la racine pour orchestrer les services et leurs dépendances.
La conteneurisation complète fera partie de la mise en place progressive de l’architecture cible.

## Green Code — pistes d’amélioration

Conformément à la demande projet, voici des actions concrètes à envisager pour appliquer une démarche Green Code :

- limiter les échanges réseau inutiles entre microservices ;
- exposer des DTOs minimaux pour réduire la taille des payloads ;
- éviter les logs verbeux en production ;
- paginer les listes volumineuses ;
- mutualiser les dépendances et supprimer les librairies non utilisées ;
- optimiser les appels base de données et éviter les requêtes redondantes ;
- mettre en place des images Docker légères ;
- désactiver les services non nécessaires en environnement local ou de test ;
- surveiller le temps de réponse et la consommation mémoire de chaque service ;
- documenter une stratégie de mise en veille ou d’arrêt des environnements non utilisés.
