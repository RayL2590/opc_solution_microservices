# Guide de test manuel — MédiLabo Solutions

> Objectif : valider de bout en bout que les Sprints 1 à 3 (Epics 1, 2, 3, 4, 5, 6) fonctionnent
> réellement, en conditions Docker (déploiement cible) et en local (dev).
> Identifiants par défaut du projet : `medilabo` / `medilabo123` (vérifier que `MEDILABO_USER`
> dans votre `.env` / `.env.docker` correspond bien à ce couple — le mot de passe en clair
> n'est jamais stocké, seul son hash BCrypt l'est).

---

## 0. Pré-requis avant de commencer

- [ ] `.env` et `.env.docker` existent à la racine (copiés depuis `.env.example` /
      `.env.docker.example`, valeurs réelles renseignées — jamais commités).
- [ ] Docker Desktop lancé (pour la Partie A) **ou** MySQL sur `3306` + MongoDB sur `27017`
      disponibles en local (pour la Partie B).
- [ ] Un terminal et un navigateur.
- [ ] Un client HTTP pour les tests API : `curl` (fourni), ou Postman/Insomnia si vous préférez une interface graphique.

---

## Partie A — Test via Docker Compose (scénario de référence)

C'est le scénario qui doit fonctionner pour valider que "`docker compose up` depuis un
clone propre doit démarrer tout le système".

### A.1 — Démarrage propre

Lancez en arrière-plan plutôt qu'attaché (`-d` = detached) — sinon le terminal reste englué
dans le flux continu de logs (MongoDB écrit un checkpoint WiredTiger toutes les minutes, entre
autres, et noie tout) :

```bash
docker compose up --build -d
```

Puis inspectez l'état service par service, sans avoir à lire un mur de texte :

```bash
# Vue d'ensemble : statut + santé de chaque conteneur
docker compose ps
```

**Attendu (`docker compose ps`) :**
- [ ] Tous les services sont `Up` (ou `Up (healthy)` pour `mysql`), aucun `Restarting`, aucun `Exited`.

Pour vérifier le démarrage de chaque service Spring Boot sans le bruit MongoDB, filtrez les logs avec `grep`/`Select-String` sur la ligne de succès plutôt que de tout lire :

```bash
# Bash
docker compose logs patient-service notes-service assessment-service front-service gateway-service \
  | grep -E "Started .*Application|ERROR|Exception"
```

```powershell
# PowerShell
docker compose logs patient-service, notes-service, assessment-service, front-service, gateway-service `
  | Select-String -Pattern "Started .*Application|ERROR|Exception"
```

**Attendu :**
- [ ] Une ligne `Started XxxApplication in N seconds` apparaît pour chacun des 5 services.
- [ ] Aucune ligne `ERROR` ou `Exception` liée au démarrage (une `WARN` isolée, comme celles
      Hibernate vues plus haut, est normale et sans impact).
- [ ] Pour Mongo spécifiquement, vérifiez juste le seed une fois (pas besoin de suivre le flux
      en continu) :
  ```bash
  docker compose logs mongo | grep -i "notesdb\|error"
  ```

Si un service échoue au démarrage, isolez ses logs seul (`docker compose logs <service>`) avant
de continuer. Pour arrêter proprement à la fin de vos tests : `docker compose down` (ajoutez
`-v` seulement si vous voulez aussi repartir d'une base vierge — ça supprime les volumes
`mysql-data`/`mongo-data`).

### A.2 — Ports exposés

```bash
docker compose ps
```

**Attendu :**
- [ ] Seul `gateway-service` publie un port sur l'hôte (`8080:8080`).
- [ ] `patient-service`, `notes-service`, `assessment-service`, `front-service` n'ont **aucun** port publié (accessibles uniquement entre conteneurs, via le réseau Compose).
- [ ] MySQL/Mongo : pas de port publié non plus par défaut dans `docker-compose.yml` actuel —
      normal, ce sont des ports de debug optionnels, pas requis pour le fonctionnement.

C'est la preuve concrète que "la Gateway est le seul point d'entrée réseau" —
si vous pouvez joindre `patient-service` directement sur un port hôte, c'est une régression.

---

## Partie B — Parcours fonctionnel via l'interface web (front-service)

Toute cette partie se fait dans le navigateur, sur `http://localhost:8080` — **la Gateway elle-même**, pas le port interne du front. La 4ᵉ route de la Gateway (`Path=/,/ui/**,/css/**,/js/**,/favicon.ico`, voir `gateway-service/src/main/resources/application-docker.yml`) route déjà tout le trafic UI vers `front-service:8084` en interne — les trois derniers prédicats couvrent les ressources statiques, sans lesquelles la page arriverait sans CSS. Aucun port supplémentaire à publier : la Gateway reste le seul point d'entrée publié sur l'hôte — l'UI est *un des chemins* derrière la Gateway, pas une exception à côté.

### B.1 — Authentification

- [ ] Ouvrir `http://localhost:8080/ui/patients` sans être authentifié → le navigateur affiche une pop-up HTTP Basic (pas un formulaire de login personnalisé — c'est voulu).
- [ ] Saisir un mauvais mot de passe → 401 / accès refusé, la pop-up réapparaît.
- [ ] Saisir `medilabo` / `medilabo123` → accès à la page.

### B.2 — Liste des patients

- [ ] La page `/ui/patients` affiche les 4 patients de seed : **TestNone**, **TestBorderline**,
      **TestInDanger**, **TestEarlyOnset** (ids 1 à 4, dans cet ordre — garanti par `data.sql`).
- [ ] Un lien "Ajouter un patient" est visible et cliquable.

### B.3 — Création d'un patient

- [ ] Cliquer sur "Ajouter un patient", remplir le formulaire avec des valeurs valides (nom, prénom, date de naissance passée, genre `M` ou `F`).
- [ ] Soumettre → redirection vers `/ui/patients` (pattern Post/Redirect/Get), le nouveau patient apparaît dans la liste.
- [ ] Retenter avec un champ obligatoire vide (ex : nom vide) → la page reste sur le formulaire, **statut HTTP 400** (vérifiable via les devtools réseau du navigateur), message d'erreur en français affiché sous le champ fautif.
- [ ] Tester une date de naissance dans le futur → doit être rejetée (validation métier
      Sprint 1 : genre M/F/U et date de naissance dans les 160 dernières années).

### B.4 — Modification d'un patient

- [ ] Depuis la fiche détail d'un patient (`/ui/patients/{id}`), cliquer "Modifier les
      informations".
- [ ] Changer un champ (ex : téléphone), soumettre → redirection vers la liste, la modification est bien reflétée si vous rouvrez la fiche détail.

### B.5 — Fiche détail patient : démographie + notes + risque

Ouvrir `/ui/patients/1` (TestNone) et vérifier que **les trois blocs suivants s'affichent
simultanément** (Sprint 3 les a tous les trois sur la même page) :

- [ ] **Démographie** : nom, prénom, date de naissance, genre, adresse/téléphone si renseignés.
- [ ] **Historique des notes** : la note de seed s'affiche avec son texte et son horodatage.
- [ ] **Risque de diabète** : une bande de risque textuelle est affichée à côté de "Risque de diabète :".

**Valeurs attendues pour les 4 patients de seed** (oracle canonique du projet — si l'une de ces valeurs diffère, il y a une régression sur `assessment-service` ou sur le seed) :

| Patient (id) | Déclencheurs attendus | Bande de risque attendue |
|---|---|---|
| TestNone (1) | 1 (`Poids`) | **None** |
| TestBorderline (2) | 2 (`Anormal`, `Réaction`) | **Borderline** |
| TestInDanger (3) | 3 (`Fumeur`, `Anormal`, `Cholestérol`) | **In Danger** |
| TestEarlyOnset (4) | 7 (`Anticorps`, `Réaction`, `Hémoglobine A1C`, `Taille`, `Poids`, `Cholestérol`, `Vertiges`) | **Early Onset** |

- [ ] Vérifier chacune des 4 fiches (`/ui/patients/1` à `/ui/patients/4`) contre ce tableau.

### B.6 — Ajout d'une note et recalcul du risque

- [ ] Sur la fiche de **TestNone** (id 1, actuellement `None`, 1 déclencheur), ajouter une note contenant le mot "Vertige" (déclencheur supplémentaire) via le formulaire "Ajouter une
      note".
- [ ] Soumettre → redirection vers la fiche détail, la nouvelle note apparaît dans l'historique.
- [ ] **Vérifier que la bande de risque a changé** (2 déclencheurs distincts maintenant :
      `Poids` + `Vertiges` — pour TestNone, née le 31/12/1966 donc âgée de 59 ans, `>30` et
      `count>=2` sans atteindre le seuil In Danger de 6 → devrait passer à **Borderline**).
      C'est la preuve concrète que l'absence de cache (recalcul systématique) fonctionne
      réellement, pas seulement en test unitaire.
- [ ] Retenter avec un champ note vide → 400, la page se re-rend avec un message d'erreur, sans
      perdre l'affichage patient/notes/risque déjà chargés.

---

## Partie C — Test direct des API (sans passer par l'UI)

Utile pour isoler un problème (front vs back) et pour vérifier des cas que l'UI ne couvre pas
facilement (404, formats d'erreur).

> **Raccourci :** tout ce que couvre cette partie — et une soixantaine de cas hostiles en plus
> (injections, payloads malformés, escalade de privilège) — est automatisé dans
> `smoke-tests.ps1` / `smoke-tests.sh`. Une commande, une ligne verte ou rouge par test :
> voir **`smoke-tests-guide.md`**. Les commandes `curl` ci-dessous restent utiles pour
> inspecter une réponse en détail ou creuser un cas précis à la main.

> Remplacer `8080` par le port de la Gateway. Les credentials sont envoyés via `-u`.

### C.1 — Patient-service (via Gateway)

```bash
# Liste des patients
curl -u medilabo:medilabo123 http://localhost:8080/patients

# Patient existant
curl -u medilabo:medilabo123 http://localhost:8080/patients/1

# Patient inexistant → 404 + ProblemDetail RFC 7807
curl -i -u medilabo:medilabo123 http://localhost:8080/patients/9999

# Sans credentials → 401
curl -i http://localhost:8080/patients/1
```

- [ ] `GET /patients` renvoie un tableau JSON de 4+ patients.
- [ ] `GET /patients/1` renvoie le patient TestNone.
- [ ] `GET /patients/9999` renvoie **404** avec un corps JSON `application/problem+json`
      contenant `"status": 404` et un `detail` mentionnant l'id.
- [ ] Sans `-u`, la requête renvoie **401**.

### C.2 — Notes-service (via Gateway)

```bash
# Notes d'un patient
curl -u medilabo:medilabo123 "http://localhost:8080/notes?patId=4"

# Note par id (récupérer un id depuis la commande précédente)
curl -u medilabo:medilabo123 http://localhost:8080/notes/<id>

# patId non-numérique → 400 (pas 500)
curl -i -u medilabo:medilabo123 "http://localhost:8080/notes?patId=abc"
```

- [ ] `GET /notes?patId=4` renvoie 4 notes pour TestEarlyOnset.
- [ ] `GET /notes?patId=abc` renvoie **400**, pas 500 (validation du paramètre côté contrôleur).
- [ ] `GET /notes?patId=9999` (patient sans notes ou inexistant) renvoie **200 + `[]`**, pas 404
      (contrat volontaire : liste vide n'est pas une erreur).

### C.3 — Assessment-service (via Gateway) — le cœur du Sprint 3

```bash
# Les 4 fixtures canoniques
curl -u medilabo:medilabo123 http://localhost:8080/assessments/1
curl -u medilabo:medilabo123 http://localhost:8080/assessments/2
curl -u medilabo:medilabo123 http://localhost:8080/assessments/3
curl -u medilabo:medilabo123 http://localhost:8080/assessments/4

# Patient inexistant → 404 (cascade depuis patient-service)
curl -i -u medilabo:medilabo123 http://localhost:8080/assessments/9999
```

- [ ] Les 4 réponses correspondent exactement au tableau de la section B.5 : `riskBand`,
      `triggerCount`, et `triggersDetected` (ordre inclus).
- [ ] Exemple de forme attendue pour le patient 4 :
  ```json
  {
    "patId": 4,
    "patient": { "firstName": "Test", "lastName": "TestEarlyOnset", "age": 24 },
    "riskBand": "Early Onset",
    "triggerCount": 7,
    "triggersDetected": ["Anticorps", "Réaction", "Hémoglobine A1C", "Taille", "Poids", "Cholestérol", "Vertiges"]
  }
  ```
  (l'`age` variera selon la date du jour où vous testez — c'est normal, il est recalculé à
  chaque appel).
- [ ] `GET /assessments/9999` renvoie **404** (le patient n'existe pas côté patient-service, et
      l'erreur cascade correctement).
- [ ] Rejouer `GET /assessments/1` deux fois de suite avec un léger délai entre les deux (ou
      après avoir ajouté une note entre les deux appels) → le résultat change si une note a été
      ajoutée. Confirme l'absence de cache.

### C.4 — Défense en profondeur : accès direct à un service interne

Ce test ne peut se faire qu'en environnement local ou en exposant temporairement un port
interne (voir A.2) — il valide que chaque service se protège **indépendamment** de la Gateway,
pas seulement via elle.

```bash
# En supposant patient-service exposé temporairement sur 8081 :
curl -i http://localhost:8081/patients/1
# Attendu : 401, PAS un accès libre — même en contournant la Gateway.
```

- [ ] Sans credentials, l'accès direct à un service back-end (en bypassant la Gateway) renvoie
      **401**, pas un accès libre. C'est la preuve de la défense en profondeur (chaque service
      applique aussi sa propre sécurité HTTP Basic, pas seulement la Gateway).

---

## Partie D — Test en local sans Docker (dev workflow)

À faire si vous voulez valider que chaque service tourne aussi de façon autonome (utile pour le
développement, pas juste pour la démo Docker).

### D.1 — Bases de données locales

- [ ] MySQL tourne sur `3306`, base `patientdb` accessible avec les credentials de `.env`.
- [ ] MongoDB tourne sur `27017`.
- [ ] Le seed MongoDB n'est monté automatiquement **que** via Docker
      (`docker-entrypoint-initdb.d`) — en local pur, il faut l'exécuter manuellement une fois :
      ```bash
      mongosh mongodb://localhost:27017 docker/mongo-init.js
      ```

### D.2 — Démarrage de chaque service

Dans 5 terminaux séparés, depuis la racine de chaque service :

```bash
cd patient-service && ./mvnw spring-boot:run     # port 8081
cd notes-service && ./mvnw spring-boot:run       # port 8082
cd assessment-service && ./mvnw spring-boot:run  # port 8083
cd front-service && ./mvnw spring-boot:run       # port 8084
cd gateway-service && ./mvnw spring-boot:run     # port 8080
```

(Sous Windows : `mvnw.cmd` au lieu de `./mvnw`.)

- [ ] Les 5 démarrent sans exception, chacun sur son port attendu.
- [ ] Rejouer les checks des Parties B et C sur `localhost` — comportement identique à Docker.

---

## Partie E — Suites de tests automatisées (complément, pas un substitut)

Ce guide teste le comportement **observé**, pas la couverture de code. Faites tourner aussi les
tests automatisés pour vérifier qu'ils passent tous avant de considérer le projet terminé :

```bash
cd patient-service && ./mvnw test      # Testcontainers démarre un MySQL éphémère (mysql:8.0)
cd notes-service && ./mvnw test        # Testcontainers démarre un Mongo éphémère (mongo:7.0)
cd assessment-service && ./mvnw test   # pas de dépendance DB, tourne toujours
cd front-service && ./mvnw test
cd gateway-service && ./mvnw test
```

- [ ] Docker Desktop est lancé — `patient-service` et `notes-service` en dépendent désormais
      pour leurs tests d'intégration (Testcontainers gère MySQL/MongoDB automatiquement, plus
      besoin d'une instance locale pré-démarrée sur `3306`/`27017`, y compris en Partie D).
- [ ] Les 5 suites passent sans échec (`BUILD SUCCESS`).

---

## Checklist de clôture

- [ ] Partie A (Docker) : démarrage propre, aucun service en boucle de redémarrage.
- [ ] Partie B (UI) : CRUD patient, notes, et affichage du risque cohérent avec le tableau
      oracle.
- [ ] Partie C (API) : les 4 fixtures canoniques d'assessment-service renvoient exactement les
      bandes attendues ; défense en profondeur vérifiée.
- [ ] Partie E (tests automatisés) : 5 suites vertes.
- [ ] Aucun secret (mot de passe, hash BCrypt) visible dans les logs consultés pendant les tests.

Si tous les points sont cochés, le projet est fonctionnellement validé de bout en bout pour les
Sprints 1 à 3. Les justifications d'architecture (NoSQL, 3NF, découpage microservices, Green
Code) sont hors périmètre de ce guide — ce sont des livrables documentaires, pas des
comportements à tester ; elles vivent dans les sections dédiées du `README.md`.
