# Guide des smoke tests API

> **En une phrase :** un script qui rejoue ~85 requêtes HTTP contre le système démarré et affiche une ligne verte ou rouge par test, pour vérifier en 30 secondes que tout va bien : sans Postman, sans clic, sans lire un mur de JSON.
Deux versions équivalentes : `smoke-tests.ps1` (PowerShell) et `smoke-tests.sh` (bash/curl) — mêmes sections, mêmes assertions, mêmes attendus. Utilisez celle de votre terminal.

---

## 1. À quoi ça sert (et à quoi ça ne sert pas)

Ce script est un **complément** aux deux autres niveaux de vérification du projet, pas un remplacement :

| Niveau | Ce que ça teste | Quand l'utiliser |
|---|---|---|
| Suites Maven (`mvn test`) | La logique **interne** de chaque service, en isolation | Avant de commiter |
| `integration-tests` (`mvn test`) | La chaîne réelle entre services, sans mock : les 5 services tournent en vrais processus | Avant de commiter un changement touchant un contrat inter-services |
| **Smoke tests (ce script)** | Le système **assemblé** tel que déployé : Gateway, sécurité, conteneurs | Après `docker compose up`, avant une démo |
| `manual-testing-guide.md` | Le **parcours utilisateur** dans le navigateur | Pour valider l'UI et répéter la démo |

La différence qui compte : les suites de chaque service mockent les appels réseau, donc elles ne peuvent pas détecter une route Gateway mal configurée, un compte de service désynchronisé ou un conteneur mal démarré. Le script, lui, ne parle qu'à `localhost:8080` — il voit le système comme le voit le jury.

`integration-tests` comble l'espace entre les deux : il démarre vraiment les cinq services et fait transiter de vraies requêtes HTTP, mais sans Docker ni conteneur applicatif. Il attrape ce qu'aucune suite isolée ne peut voir — deux services qui ne valident plus la même chose — sans exiger un système déployé.

**Ce qu'il ne teste pas :** le rendu HTML, l'ergonomie, le CSS, le comportement du navigateur. Pour ça, c'est `manual-testing-guide.md`.

---

## 2. Démarrage rapide

```powershell
# 1. Le système doit tourner
docker compose up -d

# 2. Lancer les tests
.\Documentation\smoke-tests.ps1
```

```bash
# équivalent bash
./Documentation/smoke-tests.sh
```

Sortie attendue en fin de run :

```
─────────────────────────────────────────────
  85 réussis, 0 échec
```

Sur un seed propre **avec** `MEDILABO_SVC_ASSESSMENT_PASSWORD` exporté. Sans cette variable, la section `authz` est ignorée : `79 réussis, 0 échec, 6 ignorés`.

Le script renvoie **exit code 0** si tout passe, **1** sinon — utilisable dans un pipeline.

### Options

| PowerShell | bash | Effet |
|---|---|---|
| `-Section notes` | `-s notes` | Ne joue qu'une section |
| `-ShowBody` | `-v` | Affiche le corps des réponses |
| `-Base http://...` | `BASE=http://... ` | Cible une autre URL que `localhost:8080` |

Les sections : `health`, `security`, `authz`, `patients`, `notes`, `assessment`, `edge`, `mutation`.

Un nom de section inconnu est rejeté immédiatement (**exit 2**), et non traité comme un run vide qui sortirait « 0 réussi, 0 échec » avec un code de succès — ce serait un faux vert.

### Lire la sortie

```
  v GET /patients/9999 (inexistant) → 404                    404     ← réussi
  x id non numérique → 400                    attendu 400, reçu 500  ← échoué, avec le corps
  ~ patient 1 → None                   seed modifié par un run précédent  ← ignoré, pas un bug
  - Les 4 back-ends n'ont AUCUN port publié…                          ← commentaire
```

En bash les marqueurs sont `✓ ✗ ∼ •`. Un test qui échoue affiche les premières lignes de la réponse reçue, pour diagnostiquer sans relancer la requête à la main.

---

## 3. Ce que contient chaque section

### `health` — le système répond (3 tests)
La Gateway est joignable, le front est atteint **à travers** la Gateway, le CSS est servi.
Si cette section est rouge, tout le reste le sera : commencez par `docker compose ps`.

### `security` — la porte est fermée (8 tests)
401 sans credentials, mauvais mot de passe, utilisateur inconnu, mot de passe vide — sur les trois API **et** sur l'UI. Plus un cas que le jury pourrait tenter : **rejouer le hash BCrypt comme s'il était le mot de passe** (401 attendu — le hash n'est pas un identifiant).

### `authz` — le moindre privilège (6 tests) ⭐
**La meilleure section à montrer.** Elle prouve la Story 7.1 : `svc-assessment` (compte machine) peut **lire** patients et notes — il en a besoin pour calculer un risque — mais reçoit **403** dès qu'il sort de ce périmètre :

| Requête | Attendu | Pourquoi |
|---|---|---|
| `GET /patients/1`, `GET /notes?patId=1` | 200 | ce dont il a besoin pour calculer |
| `POST /patients`, `PUT /patients/1`, `POST /notes` | 403 | il ne doit jamais écrire |
| `GET /assessments/1` | 403 | c'est son identité **sortante**, pas entrante — seul le front consomme les évaluations |

Le point à souligner à l'oral : **403 et non 401**. L'identité est valide, c'est le *droit* qui manque. Un compte machine compromis ne permettrait pas de muter la base.

Cette section est **ignorée par défaut** (le mot de passe machine n'a pas de valeur codée en dur). Pour l'activer :

```powershell
$env:MEDILABO_SVC_ASSESSMENT_PASSWORD = "<valeur de votre .env>"
.\Documentation\smoke-tests.ps1 -Section authz
```

### `patients` — contrat de lecture et d'erreur (10 tests)
Les 4 patients de seed sont là, un id inexistant rend un **404 au format ProblemDetail RFC 7807**. Puis les cas tordus : id non numérique, négatif, plus grand que `Integer.MAX_VALUE`, à virgule, verbe HTTP non supporté.

### `notes` — contrat de lecture (11 tests)
TestEarlyOnset a bien ses 4 notes. Surtout : `patId` sans note rend **200 + `[]`** et non 404 — c'est un choix de contrat assumé (« liste vide n'est pas une erreur »). Puis les paramètres malformés, et un ObjectId Mongo invalide (404, pas 500).

### `assessment` — l'oracle des 4 cas canoniques (10 tests) ⭐
**Le test de la soutenance.** Les quatre patients imposés par le sujet doivent rendre
exactement leur bande :

| Patient | Déclencheurs | Bande |
|---|---|---|
| TestNone (1) | 1 | None |
| TestBorderline (2) | 2 | Borderline |
| TestInDanger (3) | 3 | In Danger |
| TestEarlyOnset (4) | 7 | Early Onset |

La section ne vérifie pas que le code HTTP : elle contrôle le `triggerCount` et **l'ordre exact** des 7 déclencheurs de TestEarlyOnset — ce qui prouve que `triggersDetected` suit bien l'ordre chronologique de première apparition, et non l'ordre de lecture des notes (qui arrivent *most-recent-first* depuis notes-service).

### `edge` — les cas hostiles (22 tests) ⭐
La section écrite **en se mettant à la place de quelqu'un qui veut démontrer que le travail n'est pas fiable**. Le critère : sur les cas testés ici, rien ne doit sortir en **500**, parce qu'un 500 signifie « le serveur a planté » là où la faute est côté appelant.

> À dire honnêtement si on creuse : le `@ExceptionHandler(Exception.class)` de chaque service reste un filet large. Les cas fautifs identifiés ont chacun leur handler dédié (400, 405, 406, 415), mais une exception Spring non anticipée retomberait encore dans le catch-all et sortirait en 500. La bonne formulation est donc « les cas connus sont couverts et testés », pas « il est impossible d'obtenir un 500 ».

| Attaque | Attendu |
|---|---|
| SQL injection dans l'id et en query param | 400 |
| NoSQL injection `{"$ne":null}` | 400 |
| Path traversal `../../etc/passwd` | 400/404 |
| JSON tronqué, corps vide, `{}` | 400 + liste des champs fautifs |
| `Content-Type: text/plain` sur une API JSON | 415 |
| `Accept: application/xml` sur une API JSON | 406 |
| Note de 100 000 caractères | 201 ou 400, jamais un crash |
| `Authorization` corrompu, vide, ou en `Bearer` | 401 |
| Genre `X`, date au futur, il y a 300 ans, format `31/12/1990` | 400 |

### `mutation` — CRUD nominal, régression téléphone & absence de cache (15 tests) ⭐
Quatre blocs. D'abord le **chemin nominal du CRUD patient**, celui que les autres sections ne couvrent pas (elles ne testent que les rejets) : créer un patient avec des données valides, le relire, le modifier en `PUT`, vérifier que la modification est persistée.

Ensuite deux blocs ajoutés après un bug trouvé en production (voir §6) :

- **Édition d'un patient du seed, adresse seule modifiée.** Le bloc CRUD ci-dessus crée son propre patient avec un téléphone français, il n'exerçait donc jamais le cas réel : éditer un patient de seed, dont le téléphone est en `+1`. Le test relit le téléphone existant et le réémet tel quel, comme le fait le formulaire.
- **Un numéro par indicatif de `PhoneCountry`.** La liste des indicatifs est dupliquée entre `PhoneCountry` (front-service) et le regex de `PatientDTO` (patient-service). En ajouter un d'un seul côté produit un E.164 que le back refuse. Ces 6 assertions détectent la désynchronisation au prochain ajout.

Enfin la preuve de **FR-9** : affiche la bande de TestNone, ajoute une note contenant « Vertige », réaffiche la bande.

```
      avant : None
      après : Borderline
```

Le risque est recalculé à chaque appel, jamais mis en cache : une note ajoutée change
immédiatement le classement du patient.

> ⚠️ **Cette section modifie la base.** C'est voulu — on ne peut pas prouver un recalcul sans écrire. Le test **exige un état de départ à `None`** : si le patient est déjà Borderline (run précédent), il affiche `non concluant` au lieu de valider, car ajouter une note ne pourrait alors plus rien faire basculer. Voir le point suivant.

---

## 4. Le seed, et pourquoi relancer donne un résultat différent

La section `mutation` ajoute réellement une note à TestNone. Au run suivant, ce patient est donc légitimement passé à **Borderline** — il a maintenant 2 déclencheurs.

Le script **détecte ce cas à deux endroits** et refuse de conclure plutôt que d'afficher un faux vert :

```
  ~ patient 1 → None                    seed modifié par un run précédent
  ~ recalcul immédiat, sans cache       non concluant : départ Borderline, pas None
```

C'est un point important pour la crédibilité de la suite : un test qui ne vérifierait que l'état *final* (`Borderline`) passerait au vert même si rien n'avait bougé — il « prouverait » un recalcul qui n'a pas eu lieu. Ici le test exige `None` au départ **et** `Borderline` à l'arrivée ; à défaut il sort en « ignoré », jamais en réussi.

Ce n'est donc **pas une régression**, et ce n'est pas non plus un succès. Pour repartir d'un état propre :

```powershell
docker compose down -v
docker compose up -d
```

Le `-v` est indispensable : il supprime les volumes `mysql-data` et `mongo-data`, ce qui rejoue `data.sql` et `mongo-init.js`.

**Avant une démo devant le jury**, faites toujours ce reset : la section `assessment` affichera alors les quatre bandes canoniques exactes.

---

## 5. Diagnostiquer un échec

**Tout est rouge, « pas de réponse — service down ? »**
Le système n'est pas démarré, ou pas encore prêt. Les services Spring mettent ~30 s.
```powershell
docker compose ps          # tous "Up", mysql "Up (healthy)"
docker compose logs gateway-service | Select-String "Started|ERROR"
```

**Un 401 inattendu sur les appels inter-services**
Typiquement `MEDILABO_SVC_*_PASSWORD` et `MEDILABO_SVC_*_PASSWORD_BCRYPT` qui ne
correspondent plus : le hash doit être celui du mot de passe en clair. Voir §2 du README, section Sécurité.

**Un 500 quelque part**
C'est le signal qui compte. Le corps de la réponse est affiché sous le test ; pour la cause exacte :
```powershell
docker compose logs <service> | Select-String "Unhandled exception" -Context 0,3
```
C'est précisément comme ça que les quatre 500 corrigés en août 2026 ont été diagnostiqués
(voir §6).

**front-service ou assessment-service ne démarrent pas**
Ces deux-là refusent de booter sans `MEDILABO_SVC_FRONT_PASSWORD` /
`MEDILABO_SVC_ASSESSMENT_PASSWORD` — ces variables n'ont volontairement aucune valeur par défaut. Vérifiez que `.env` existe à la racine.

---

## 6. Ce que ces tests ont déjà trouvé

À signaler à l'oral si on vous demande comment vous vérifiez votre travail, c'est une meilleure réponse que « tout était vert du premier coup ».

Au premier run réel, la suite a révélé **quatre défauts authentiques**, tous de même cause : le handler `@ExceptionHandler(Exception.class)` attrapait aussi les exceptions **de Spring lui-même**, qui auraient dû produire leur propre code HTTP.

| Cas | Avant | Après |
|---|---|---|
| `DELETE /patients/1` | 500 | **405** |
| Date envoyée en `31/12/1990` | 500 | **400** |
| `Content-Type: text/plain` | 500 | **415** |
| `/assessments/abc` | 500 | **400** |

Le dernier était le plus gênant : patient-service et notes-service rendaient bien 400 sur un identifiant non numérique, mais assessment-service sortait 500 sur le même cas, une incohérence entre services, invisible sans test transverse. Ces quatre corrections ont chacune leur test unitaire dans le `GlobalExceptionHandlerTest` du service concerné.

### Le bug que cette suite n'a **pas** trouvé

Plus instructif que les précédents, parce qu'il a été découvert à la main, en production, sur un cas trivial : **modifier la seule adresse d'un patient de seed** renvoyait une page d'erreur Whitelabel (500), avec une erreur de validation sur le téléphone — un champ jamais touché.

La cause : la liste des indicatifs téléphoniques existe **à deux endroits**, `PhoneCountry` (front-service) et le regex de `PatientDTO` (patient-service). Le front normalisait le numéro de seed en `+1…`, que le regex du back ne connaissait pas. Chaque moitié du contrat était correcte et testée ; que les deux moitiés soient **d'accord entre elles** ne l'était nulle part.

Pourquoi rien ne l'a vu :

| Garde-fou | Pourquoi il est resté vert |
|---|---|
| `PhoneNormalizerTest` | vérifiait que le front produit `+1…` — correct, isolément |
| `PatientControllerTest` | vérifiait que le back accepte `+33…` — correct, isolément |
| `PatientUiControllerTest` | `@WebMvcTest` avec le service **mocké** : un mock ne rejette jamais |
| Smoke tests, section `mutation` | créait son **propre** patient avec un téléphone français, sans jamais éditer un patient de seed |
| `integration-tests` | ne faisait que des **GET** — le bug était en écriture |

Trois enseignements, qui valent au-delà de ce bug précis :

1. **Un contrat dupliqué sans test de cohérence finit par diverger.** Aucune suite unitaire ne peut l'attraper par construction : il faut un test qui traverse les deux services.
2. **Tester le rejet ne prouve pas l'acceptation.** La suite avait bien un test « téléphone au mauvais format → 400 », mais aucun ne vérifiait qu'un bon format passe.
3. **Le seed est le seul jeu de données que le jury verra.** Un chemin non couvert sur des données créées par le test est un risque modéré ; sur les données de démo, c'est le premier écran de l'évaluation.

Corrections apportées après coup, toutes vérifiées en cassant volontairement le regex pour confirmer qu'elles échouent bien :

- section `mutation` : édition d'un patient de seed + un numéro par indicatif supporté ;
- `integration-tests` : deux tests d'**écriture** traversant front → Gateway → patient-service ;
- front-service : les erreurs 4xx venues d'un service amont re-rendent le formulaire avec le message sous le bon champ, **au lieu d'une page Whitelabel** (un 5xx amont reste, lui, volontairement non rattrapé — c'est une panne, pas une faute de saisie).

---

## 7. Ajouter un test

Les deux scripts exposent deux helpers :

```powershell
# Vérifie le code HTTP ; plusieurs codes acceptés en les séparant par une virgule
Check 'libellé du test' '404' 'GET' '/patients/9999' -Auth $UserAuth

# Vérifie le CORPS de la réponse (regex) — "répond juste", pas seulement "répond"
Contains 'la bande est None' '"riskBand"\s*:\s*"None"' 'GET' '/assessments/1' -Auth $UserAuth
```

```bash
check    "libellé du test" "404"  GET /patients/9999 -u "$USER_AUTH"
contains "la bande est None" '"riskBand"[[:space:]]*:[[:space:]]*"None"' GET /assessments/1 -u "$USER_AUTH"
```

Deux pièges rencontrés en écrivant ces scripts, à connaître avant d'ajouter un test :

- **Les accents.** En bash, `grep` en locale C raisonne en **octets** : un `é` UTF-8 en fait deux, donc un `.` simple ne le couvre pas — d'où les `.?.?` dans le test d'ordre des déclencheurs. Le motif reste strict sur l'ordre, il ne se relâche que sur l'encodage.
- **Les gros payloads.** Passer 100 Ko en argument de ligne de commande dépasse la limite de Windows (~32 Ko) et frôle `ARG_MAX` sous Unix : les corps volumineux passent par un fichier temporaire avec `--data-binary @fichier`.

Gardez les deux scripts synchronisés : ils sont censés tester exactement la même chose.

---

## 8. Checklist avant la soutenance

- [ ] `docker compose down -v ; docker compose up -d` — repartir d'un seed propre
- [ ] Attendre ~30 s, vérifier `docker compose ps` (tous `Up`, mysql `Up (healthy)`)
- [ ] Exporter `MEDILABO_SVC_ASSESSMENT_PASSWORD` (sinon la section `authz` est ignorée)
- [ ] `.\Documentation\smoke-tests.ps1` → **0 échec, 0 ignoré**
- [ ] Vérifier que la section 7 affiche bien `avant : None → après : Borderline` — si elle
      affiche `non concluant`, le seed n'était pas propre
- [ ] `docker compose down -v ; docker compose up -d` — re-nettoyer après la section `mutation`
- [ ] Les 5 suites Maven au vert (`mvn test` dans chaque service)
- [ ] `integration-tests` au vert — nécessite un `mvn install -DskipTests` préalable des 5 services (le test lance leurs `-exec.jar`), et un daemon Docker pour Testcontainers

Si Docker refuse de démarrer le jour J, ce script est aussi votre **plan B** : il montre en une capture que le système répondait correctement, y compris sur les cas hostiles.
