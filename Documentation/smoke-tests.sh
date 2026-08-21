#!/usr/bin/env bash
# =============================================================================
# MédiLabo Solutions — smoke tests API (curl)
#
# But : voir en ~30 secondes, sans Postman, que tout le système répond
# correctement — y compris sur les cas tordus qu'un examinateur hostile
# essaierait pour démontrer que le travail n'est pas fiable.
#
# Usage :
#   ./Documentation/smoke-tests.sh                  # tout, via la Gateway
#   ./Documentation/smoke-tests.sh -v               # + corps des réponses
#   ./Documentation/smoke-tests.sh -s security      # une seule section
#   BASE=http://localhost:8080 ./Documentation/smoke-tests.sh
#
# Sections : health, security, authz, patients, notes, assessment, edge, mutation
#
# Documentation complète : Documentation/smoke-tests-guide.md
# Pré-requis : `docker compose up -d` lancé, et un `.env` renseigné.
# Le script ne lit AUCUN secret dans un fichier : les credentials viennent de
# variables d'environnement, avec les valeurs de démo par défaut.
# =============================================================================

set -uo pipefail

BASE="${BASE:-http://localhost:8080}"

# Compte clinicien (ROLE_USER)
USER_AUTH="${MEDILABO_USER:-medilabo}:${MEDILABO_PASSWORD:-medilabo123}"
# Comptes machine (Story 7.1) — utilisés ici pour PROUVER le moindre privilège.
SVC_ASSESSMENT_AUTH="${MEDILABO_SVC_ASSESSMENT_USER:-svc-assessment}:${MEDILABO_SVC_ASSESSMENT_PASSWORD:-}"

VALID_SECTIONS="health security authz patients notes assessment edge mutation"

VERBOSE=0
ONLY=""
while getopts "vs:h" opt; do
  case $opt in
    v) VERBOSE=1 ;;
    s) ONLY="$OPTARG" ;;
    h) sed -n '2,20p' "$0"; exit 0 ;;
    *) exit 2 ;;
  esac
done

# Un nom de section inconnu ne doit PAS sortir en "0 réussi, 0 échec, exit 0" : ça ressemble
# à un succès alors que rien n'a tourné. On refuse tout de suite.
if [[ -n "$ONLY" ]] && ! grep -qw -- "$ONLY" <<<"$VALID_SECTIONS"; then
  printf 'Section inconnue : "%s"\nSections valides : %s\n' "$ONLY" "$VALID_SECTIONS" >&2
  exit 2
fi

PASS=0; FAIL=0; SKIP=0
FAILED_TESTS=()

if [[ -t 1 ]]; then
  G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; B=$'\e[1m'; D=$'\e[2m'; N=$'\e[0m'
else
  G=""; R=""; Y=""; B=""; D=""; N=""
fi

section() {
  [[ -n "$ONLY" && "$ONLY" != "$1" ]] && return 1
  printf '\n%s──  %s  ──%s\n' "$B" "$2" "$N"
  return 0
}

# check <libellé> <attendu> <méthode> <chemin> [curl args...]
# Attendu : un code HTTP ("200"), ou plusieurs séparés par | ("200|204").
check() {
  local label="$1" expect="$2" method="$3" path="$4"; shift 4
  local body_file; body_file=$(mktemp)
  local code
  code=$(curl -s -o "$body_file" -w '%{http_code}' --max-time 20 -X "$method" "$BASE$path" "$@" 2>/dev/null)

  if [[ "$code" == "000" ]]; then
    printf '  %s✗%s %-58s %s(pas de réponse — service down ?)%s\n' "$R" "$N" "$label" "$R" "$N"
    FAIL=$((FAIL+1)); FAILED_TESTS+=("$label")
    rm -f "$body_file"; return 1
  fi

  if [[ "$code" =~ ^(${expect})$ ]]; then
    printf '  %s✓%s %-58s %s%s%s\n' "$G" "$N" "$label" "$D" "$code" "$N"
    PASS=$((PASS+1))
    [[ $VERBOSE -eq 1 ]] && sed 's/^/      /' "$body_file" | head -12
  else
    printf '  %s✗%s %-58s %sattendu %s, reçu %s%s\n' "$R" "$N" "$label" "$R" "$expect" "$code" "$N"
    FAIL=$((FAIL+1)); FAILED_TESTS+=("$label (attendu $expect, reçu $code)")
    sed 's/^/      /' "$body_file" | head -6
  fi
  rm -f "$body_file"
}

# contains <libellé> <motif> <méthode> <chemin> [curl args...]
# Vérifie le CORPS de la réponse, pas seulement le code — c'est ce qui distingue
# "le service répond" de "le service répond juste".
contains() {
  local label="$1" pattern="$2" method="$3" path="$4"; shift 4
  local body; body=$(curl -s --max-time 20 -X "$method" "$BASE$path" "$@" 2>/dev/null)

  if grep -qE "$pattern" <<<"$body"; then
    printf '  %s✓%s %-58s %sok%s\n' "$G" "$N" "$label" "$D" "$N"
    PASS=$((PASS+1))
    [[ $VERBOSE -eq 1 ]] && sed 's/^/      /' <<<"$body" | head -12
  else
    printf '  %s✗%s %-58s %smotif absent : %s%s\n' "$R" "$N" "$label" "$R" "$pattern" "$N"
    FAIL=$((FAIL+1)); FAILED_TESTS+=("$label (motif absent : $pattern)")
    sed 's/^/      /' <<<"$body" | head -6
  fi
}

note() { printf '  %s•%s %s\n' "$Y" "$N" "$1"; }

# =============================================================================
if section health "0. Le système répond"; then
  check "Gateway joignable (401 attendu sans creds)"    "401"     GET /patients
  check "front-service atteint via la Gateway"          "200"     GET /ui/patients -u "$USER_AUTH"
  # La Gateway laisse passer /css/** en permitAll, mais front-service exige une auth sur
  # TOUTE requête : le CSS est donc protégé de bout en bout. Le navigateur l'obtient quand
  # même, car il rejoue l'Authorization sur chaque ressource de la page.
  check "CSS servi via la Gateway (authentifié)"        "200|304" GET /css/style.css -u "$USER_AUTH"
fi

# =============================================================================
if section security "1. Authentification — la porte est bien fermée"; then
  check "sans credentials → 401"                    "401" GET /patients/1
  check "mauvais mot de passe → 401"                "401" GET /patients/1 -u "medilabo:mauvais"
  check "utilisateur inconnu → 401"                 "401" GET /patients/1 -u "pirate:pirate"
  check "mot de passe vide → 401"                   "401" GET /patients/1 -u "medilabo:"
  check "notes protégées aussi → 401"               "401" GET "/notes?patId=1"
  check "assessments protégés aussi → 401"          "401" GET /assessments/1
  check "l'UI est protégée aussi → 401"             "401" GET /ui/patients

  note "Les 4 back-ends n'ont AUCUN port publié : docker compose ps le confirme."
  note "Ci-dessous : le hash BCrypt ne doit jamais être accepté comme mot de passe."
  check "hash BCrypt joué comme mot de passe → 401"  "401" GET /patients/1 \
        -u 'medilabo:$2a$10$GzMGhp/NWTujVhv4VyYh9eM.aia95IXMsse7Yl6jUC3DC42/VIinq'
fi

# =============================================================================
if section authz "2. Autorisation — le moindre privilège (Story 7.1)"; then
  if [[ "$SVC_ASSESSMENT_AUTH" == *: ]]; then
    printf '  %s∼%s %s\n' "$Y" "$N" "MEDILABO_SVC_ASSESSMENT_PASSWORD non défini — section ignorée."
    note "Exportez-le depuis votre .env pour jouer ces tests :"
    note "  export MEDILABO_SVC_ASSESSMENT_PASSWORD=..."
    SKIP=$((SKIP+6))
  else
    note "svc-assessment a le droit de LIRE patients et notes (il en a besoin pour calculer)…"
    check "svc-assessment GET /patients/1 → 200"        "200" GET /patients/1 -u "$SVC_ASSESSMENT_AUTH"
    check "svc-assessment GET /notes?patId=1 → 200"     "200" GET "/notes?patId=1" -u "$SVC_ASSESSMENT_AUTH"

    note "…mais PAS celui d'écrire. Un compte machine volé ne doit pas pouvoir muter la base."
    check "svc-assessment POST /patients → 403"         "403" POST /patients -u "$SVC_ASSESSMENT_AUTH" \
          -H 'Content-Type: application/json' \
          -d '{"firstName":"Pirate","lastName":"Escalade","dateOfBirth":"1990-01-01","gender":"M"}'
    check "svc-assessment PUT /patients/1 → 403"        "403" PUT /patients/1 -u "$SVC_ASSESSMENT_AUTH" \
          -H 'Content-Type: application/json' \
          -d '{"firstName":"Pirate","lastName":"Escalade","dateOfBirth":"1990-01-01","gender":"M"}'
    check "svc-assessment POST /notes → 403"            "403" POST /notes -u "$SVC_ASSESSMENT_AUTH" \
          -H 'Content-Type: application/json' \
          -d '{"patId":1,"patient":"TestNone","note":"injection"}'

    note "…et pas non plus celui de consommer les évaluations : c'est le rôle du front."
    check "svc-assessment GET /assessments/1 → 403"     "403" GET /assessments/1 -u "$SVC_ASSESSMENT_AUTH"

    note "403 et non 401 : l'identité est valide, c'est le DROIT qui manque."
  fi
fi

# =============================================================================
if section patients "3. patient-service — lecture & contrat d'erreur"; then
  check "GET /patients → 200"                        "200" GET /patients -u "$USER_AUTH"
  contains "les 4 patients de seed sont là"          "TestNone.*TestBorderline.*TestInDanger.*TestEarlyOnset" \
           GET /patients -u "$USER_AUTH"
  check "GET /patients/1 → 200"                      "200" GET /patients/1 -u "$USER_AUTH"
  check "GET /patients/9999 (inexistant) → 404"      "404" GET /patients/9999 -u "$USER_AUTH"
  contains "404 au format ProblemDetail RFC 7807"    '"status"[[:space:]]*:[[:space:]]*404' \
           GET /patients/9999 -u "$USER_AUTH"

  note "Cas tordus : ce qui fait typiquement sauter un service en 500."
  check "id non numérique (/patients/abc) → 400"     "400" GET /patients/abc -u "$USER_AUTH"
  check "id négatif → 404 (pas 500)"                 "404" GET /patients/-1 -u "$USER_AUTH"
  check "id géant (> Integer.MAX) → 404 (pas 500)"   "404" GET /patients/99999999999 -u "$USER_AUTH"
  check "id à virgule (/patients/1.5) → 400"         "400" GET /patients/1.5 -u "$USER_AUTH"
  check "méthode non supportée (DELETE) → 405|403"   "405|403" DELETE /patients/1 -u "$USER_AUTH"
fi

# =============================================================================
if section notes "4. notes-service — contrat de lecture"; then
  check "GET /notes?patId=4 → 200"                   "200" GET "/notes?patId=4" -u "$USER_AUTH"
  contains "TestEarlyOnset a bien 4 notes"           '("patId"[[:space:]]*:[[:space:]]*4.*){4}' \
           GET "/notes?patId=4" -u "$USER_AUTH"

  note "Le contrat volontaire : liste vide ≠ erreur."
  check "patId sans aucune note → 200 (pas 404)"     "200" GET "/notes?patId=9999" -u "$USER_AUTH"
  contains "…et le corps est bien un tableau vide"   '^\[\]$' GET "/notes?patId=9999" -u "$USER_AUTH"

  note "Cas tordus sur le paramètre — doivent donner 400, jamais 500."
  check "patId non numérique → 400"                  "400" GET "/notes?patId=abc" -u "$USER_AUTH"
  check "patId absent → 400"                         "400" GET "/notes" -u "$USER_AUTH"
  check "patId vide (?patId=) → 400"                 "400" GET "/notes?patId=" -u "$USER_AUTH"
  check "patId négatif → 200 + [] (documenté)"       "200" GET "/notes?patId=-1" -u "$USER_AUTH"
  check "patId débordant l'Integer → 400"            "400" GET "/notes?patId=99999999999" -u "$USER_AUTH"
  check "id Mongo inexistant → 404"                  "404" GET "/notes/000000000000000000000000" -u "$USER_AUTH"
  check "id Mongo malformé → 404 (pas 500)"          "404" GET "/notes/pas-un-objectid" -u "$USER_AUTH"
fi

# =============================================================================
if section assessment "5. assessment-service — l'oracle des 4 cas canoniques"; then
  note "LE test de la soutenance : les 4 bandes imposées par le sujet."

  # Le patient 1 est le seul que la section "mutation" modifie. Si une note "Vertige" a
  # déjà été ajoutée par un run précédent, il est légitimement passé à Borderline — ce
  # n'est pas une régression, c'est un seed sale. On le détecte au lieu de crier au loup.
  p1_body=$(curl -s --max-time 20 "$BASE/assessments/1" -u "$USER_AUTH" 2>/dev/null)
  if grep -qE '"riskBand"[[:space:]]*:[[:space:]]*"Borderline"' <<<"$p1_body" \
     && grep -q 'Vertiges' <<<"$p1_body"; then
    printf '  %s∼%s %-58s %sseed modifié par un run précédent%s\n' "$Y" "$N" "patient 1 → None" "$Y" "$N"
    note "Repartez d'un seed propre : docker compose down -v ; docker compose up -d"
    SKIP=$((SKIP+2))
  else
    contains "patient 1 → None"           '"riskBand"[[:space:]]*:[[:space:]]*"None"' GET /assessments/1 -u "$USER_AUTH"
    contains "patient 1 → triggerCount 1" '"triggerCount"[[:space:]]*:[[:space:]]*1'  GET /assessments/1 -u "$USER_AUTH"
  fi

  contains "patient 2 → Borderline"  '"riskBand"[[:space:]]*:[[:space:]]*"Borderline"'  GET /assessments/2 -u "$USER_AUTH"
  contains "patient 3 → In Danger"   '"riskBand"[[:space:]]*:[[:space:]]*"In Danger"'   GET /assessments/3 -u "$USER_AUTH"
  contains "patient 4 → Early Onset" '"riskBand"[[:space:]]*:[[:space:]]*"Early Onset"' GET /assessments/4 -u "$USER_AUTH"

  note "Le compte de déclencheurs, pas seulement la bande."
  contains "patient 4 → triggerCount 7" '"triggerCount"[[:space:]]*:[[:space:]]*7' GET /assessments/4 -u "$USER_AUTH"

  note "L'ORDRE de triggersDetected est chronologique — pas l'ordre de lecture des notes."
  # Accents remplacés par ".?.?" : le test vérifie l'ORDRE, pas l'encodage. En locale C,
  # grep raisonne en octets et un "é" UTF-8 en fait deux — un "." simple ne le couvrirait pas.
  contains "patient 4 : ordre exact des 7 déclencheurs" \
           '"Anticorps".*"R.?.?action".*"H.?.?moglobine A1C".*"Taille".*"Poids".*"Cholest.?.?rol".*"Vertiges"' \
           GET /assessments/4 -u "$USER_AUTH"

  note "L'âge est recalculé à chaque appel, jamais stocké (3NF)."
  contains "l'enveloppe porte bien un âge"  '"age"[[:space:]]*:[[:space:]]*[0-9]+' GET /assessments/4 -u "$USER_AUTH"

  note "Cascade d'erreurs depuis les upstreams."
  check "patient inexistant → 404 (cascade)"    "404" GET /assessments/9999 -u "$USER_AUTH"
  check "patId non numérique → 400"             "400" GET /assessments/abc  -u "$USER_AUTH"
fi

# =============================================================================
if section edge "6. Cas hostiles — ce qu'un examinateur essaierait"; then
  note "Injections : elles doivent être inoffensives, jamais un 500."
  check "SQL injection dans l'id → 400"          "400" GET "/patients/1%20OR%201=1" -u "$USER_AUTH"
  check "SQL injection en paramètre notes → 400" "400" GET "/notes?patId=1%3B%20DROP%20TABLE%20patient" -u "$USER_AUTH"
  check "NoSQL injection (\$ne) → 400"           "400" GET '/notes?patId=%7B%22%24ne%22%3Anull%7D' -u "$USER_AUTH"
  check "path traversal → 400|404"               "400|404" GET "/patients/..%2F..%2Fetc%2Fpasswd" -u "$USER_AUTH"

  note "Corps de requête malformés — 400 attendu, jamais 500."
  check "JSON invalide sur POST /notes → 400"    "400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' -d '{"patId":1,"note":'
  check "corps vide sur POST /notes → 400"       "400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' -d ''
  check "champs obligatoires manquants → 400"    "400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' -d '{}'
  contains "…et le 400 liste les champs fautifs" '"errors"' POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' -d '{}'
  check "note vide (que des espaces) → 400"      "400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' -d '{"patId":1,"patient":"TestNone","note":"   "}'
  check "mauvais Content-Type → 415|400"         "415|400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: text/plain' -d 'ceci nest pas du json'
  check "Accept non produisible (xml) → 406"     "406" GET /patients/1 -u "$USER_AUTH" \
        -H 'Accept: application/xml'

  note "Validation métier de patient-service."
  check "genre invalide (X) → 400"               "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"A","lastName":"B","dateOfBirth":"1990-01-01","gender":"X"}'
  check "date de naissance dans le futur → 400"  "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"A","lastName":"B","dateOfBirth":"2099-01-01","gender":"M"}'
  check "naissance il y a 300 ans → 400"         "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"A","lastName":"B","dateOfBirth":"1726-01-01","gender":"M"}'
  check "téléphone au mauvais format → 400"      "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"A","lastName":"B","dateOfBirth":"1990-01-01","gender":"M","phone":"06 01 02 03 04"}'
  check "prénom vide → 400"                      "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"","lastName":"B","dateOfBirth":"1990-01-01","gender":"M"}'
  check "date au mauvais format → 400"           "400" POST /patients -u "$USER_AUTH" \
        -H 'Content-Type: application/json' \
        -d '{"firstName":"A","lastName":"B","dateOfBirth":"31/12/1990","gender":"M"}'

  note "Robustesse : gros payload, UTF-8, header Authorization corrompu."
  # Payload passé par fichier (@) : 100 Ko en argument de ligne de commande frôle ARG_MAX.
  # patId=99999 volontairement : si le service accepte (201), la note est persistée. On la
  # rattache donc à un patient inexistant plutôt qu'à TestNone, sinon cette section
  # polluerait le seed que la section "mutation" doit trouver intact.
  big_note=$(mktemp)
  printf '{"patId":99999,"patient":"SmokeBigPayload","note":"%s"}' \
         "$(head -c 100000 /dev/zero | tr '\0' 'a')" > "$big_note"
  check "note de 100 000 caractères → 201|400"   "201|400" POST /notes -u "$USER_AUTH" \
        -H 'Content-Type: application/json' --data-binary "@$big_note"
  rm -f "$big_note"
  check "Authorization malformé → 401"           "401" GET /patients/1 -H 'Authorization: Basic pas-du-base64'
  check "Authorization vide → 401"               "401" GET /patients/1 -H 'Authorization: '
  check "schéma Bearer au lieu de Basic → 401"   "401" GET /patients/1 -H 'Authorization: Bearer un-faux-jwt'
  check "route inexistante → 404"                "404" GET /route-qui-nexiste-pas -u "$USER_AUTH"
fi

# =============================================================================
if section mutation "7. Écriture & recalcul — la preuve de l'absence de cache"; then
  note "CRUD patient complet : créer, relire, modifier — le chemin nominal, pas que les erreurs."

  new_patient='{"firstName":"Smoke","lastName":"Test","dateOfBirth":"1980-05-15","gender":"F","address":"1 rue du Test","phone":"+33601020304"}'
  created=$(curl -s --max-time 20 -X POST "$BASE/patients" -u "$USER_AUTH" \
                 -H 'Content-Type: application/json' -d "$new_patient" 2>/dev/null)
  new_id=$(sed -nE 's/.*"id"[[:space:]]*:[[:space:]]*([0-9]+).*/\1/p' <<<"$created")

  if [[ -n "$new_id" ]]; then
    printf '  %s✓%s %-58s %sid=%s%s\n' "$G" "$N" "POST /patients (données valides)" "$D" "$new_id" "$N"
    PASS=$((PASS+1))
    check    "le patient créé est relisible (GET)"   "200" GET "/patients/$new_id" -u "$USER_AUTH"
    contains "les données créées sont correctes"     '"lastName"[[:space:]]*:[[:space:]]*"Test"' \
             GET "/patients/$new_id" -u "$USER_AUTH"
    check    "PUT /patients/{id} (modification) → 200" "200" PUT "/patients/$new_id" -u "$USER_AUTH" \
             -H 'Content-Type: application/json' \
             -d '{"firstName":"Smoke","lastName":"Modifie","dateOfBirth":"1980-05-15","gender":"F","address":"2 rue du Test","phone":"+33601020305"}'
    contains "la modification est bien persistée"    '"lastName"[[:space:]]*:[[:space:]]*"Modifie"' \
             GET "/patients/$new_id" -u "$USER_AUTH"
  else
    printf '  %s✗%s %-58s %spas d'"'"'id dans la réponse%s\n' "$R" "$N" "POST /patients (données valides)" "$R" "$N"
    FAIL=$((FAIL+1)); FAILED_TESTS+=("POST /patients (création)")
    SKIP=$((SKIP+4))
  fi

  note "FR-9 : ajouter une note doit changer la bande IMMÉDIATEMENT, sans cache."

  band_of() { sed -nE 's/.*"riskBand"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/p' <<<"$1"; }

  before=$(curl -s --max-time 20 "$BASE/assessments/1" -u "$USER_AUTH" 2>/dev/null)
  band_before=$(band_of "$before"); band_before=${band_before:-?}
  echo "      avant : $band_before"

  # L'état initial est vérifié AVANT d'écrire : sur un seed propre TestNone est à None.
  # S'il est déjà Borderline (run précédent), ajouter une note ne pourrait plus rien faire
  # basculer — on s'abstient donc d'écrire, pour ne pas polluer davantage un seed déjà sale.
  if [[ "$band_before" != "None" ]]; then
    printf '  %s∼%s %-58s %snon concluant : départ %s, pas None%s\n' \
           "$Y" "$N" "recalcul immédiat, sans cache" "$Y" "$band_before" "$N"
    note "Seed déjà modifié : docker compose down -v ; docker compose up -d, puis relancer."
    note "Aucune note ajoutée — le seed n'est pas pollué davantage."
    SKIP=$((SKIP+2))
  else
    check "POST d'une note 'Vertige' sur TestNone → 201" "201" POST /notes -u "$USER_AUTH" \
          -H 'Content-Type: application/json' \
          -d '{"patId":1,"patient":"TestNone","note":"Le patient se plaint de Vertige depuis peu"}'

    after=$(curl -s --max-time 20 "$BASE/assessments/1" -u "$USER_AUTH" 2>/dev/null)
    band_after=$(band_of "$after"); band_after=${band_after:-?}
    echo "      après : $band_after"

    if [[ "$band_after" == "Borderline" ]]; then
      printf '  %s✓%s %-58s %s%s → %s%s\n' \
             "$G" "$N" "recalcul immédiat, sans cache" "$D" "$band_before" "$band_after" "$N"
      PASS=$((PASS+1))
    else
      printf '  %s✗%s %-58s %s%s → %s, Borderline attendu%s\n' \
             "$R" "$N" "recalcul immédiat, sans cache" "$R" "$band_before" "$band_after" "$N"
      FAIL=$((FAIL+1)); FAILED_TESTS+=("recalcul du risque ($band_before → $band_after)")
    fi
  fi

  note "Le matching ignore casse ET accents — 'vertige' minuscule sans accent compte aussi."
  note "Limite connue et assumée : pas de gestion de la négation ('pas de vertiges' compte)."

  printf '\n  %sÉtat modifié.%s Pour revenir au seed : %sdocker compose down -v && docker compose up -d%s\n' \
         "$Y" "$N" "$D" "$N"
fi

# =============================================================================
printf '\n%s─────────────────────────────────────────────%s\n' "$B" "$N"
if [[ $FAIL -eq 0 ]]; then
  printf '%s  %d réussis, 0 échec' "$G" "$PASS"
  [[ $SKIP -gt 0 ]] && printf ', %d ignorés' "$SKIP"
  printf '%s\n\n' "$N"
  exit 0
else
  printf '%s  %d réussis, %d ÉCHECS' "$R" "$PASS" "$FAIL"
  [[ $SKIP -gt 0 ]] && printf ', %d ignorés' "$SKIP"
  printf '%s\n' "$N"
  for t in "${FAILED_TESTS[@]}"; do printf '    %s✗%s %s\n' "$R" "$N" "$t"; done
  printf '\n'
  exit 1
fi
