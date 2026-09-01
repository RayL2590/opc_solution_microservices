# =============================================================================
# MédiLabo Solutions — smoke tests API (PowerShell)
#
# Équivalent Windows natif de smoke-tests.sh, pour ne pas dépendre de Git Bash le jour de la soutenance. Mêmes sections, mêmes assertions.
#
# Usage :
#   .\Documentation\smoke-tests.ps1
#   .\Documentation\smoke-tests.ps1 -ShowBody          # affiche le corps des réponses
#   .\Documentation\smoke-tests.ps1 -Section security
#   .\Documentation\smoke-tests.ps1 -Base http://localhost:8080
#
# Sections : health, security, authz, patients, notes, assessment, edge, mutation
#
# Documentation complète : Documentation/smoke-tests-guide.md
# Pré-requis : `docker compose up -d` lancé.
# Aucun secret n'est lu depuis un fichier : les credentials viennent de variables d'environnement, avec les valeurs de démo par défaut.
# =============================================================================

[CmdletBinding()]
param(
    [string]$Base = $(if ($env:BASE) { $env:BASE } else { 'http://localhost:8080' }),
    [string]$Section = '',
    [switch]$ShowBody
)

$ErrorActionPreference = 'Continue'

$UserAuth = "$(if ($env:MEDILABO_USER) { $env:MEDILABO_USER } else { 'medilabo' }):" +
            "$(if ($env:MEDILABO_PASSWORD) { $env:MEDILABO_PASSWORD } else { 'medilabo123' })"
$SvcAssessmentAuth = "$(if ($env:MEDILABO_SVC_ASSESSMENT_USER) { $env:MEDILABO_SVC_ASSESSMENT_USER } else { 'svc-assessment' }):" +
                     "$($env:MEDILABO_SVC_ASSESSMENT_PASSWORD)"

$script:Pass = 0
$script:Fail = 0
$script:Skip = 0
$script:Failed = @()

$ValidSections = @('health', 'security', 'authz', 'patients', 'notes', 'assessment', 'edge', 'mutation')

# Un nom de section inconnu ne doit PAS sortir en "0 réussi, 0 échec, exit 0" : ça ressemble à un succès alors que rien n'a tourné. On refuse tout de suite.
if ($Section -and $ValidSections -notcontains $Section) {
    Write-Host "Section inconnue : `"$Section`"" -ForegroundColor Red
    Write-Host "Sections valides : $($ValidSections -join ', ')" -ForegroundColor Yellow
    exit 2
}

function Test-Section {
    param([string]$Name, [string]$Title)
    if ($Section -and $Section -ne $Name) { return $false }
    Write-Host ''
    Write-Host "──  $Title  ──" -ForegroundColor White
    return $true
}

# Invoke-Api : renvoie un objet {Code, Body}. Un service down donne Code = 0.
function Invoke-Api {
    param(
        [string]$Method, [string]$Path,
        [string]$Auth = '', [string]$Body = '',
        [string]$ContentType = '', [hashtable]$Headers = @{}
    )
    # Le corps va dans un fichier, pas sur stdout : un gros payload (100 Ko) fait sinon planter PowerShell sur "StandardOutputEncoding is only supported when standard output is redirected". Seul le code HTTP revient par stdout. 
    # --data-binary @fichier : passer 100 Ko en argument de ligne de commande dépasse la limite de Windows (~32 Ko).
    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpIn = $null

    $curlArgs = @('-s', '-o', $tmpOut, '-w', '%{http_code}', '-X', $Method, "$Base$Path", '--max-time', '20')
    if ($Auth)        { $curlArgs += @('-u', $Auth) }
    if ($ContentType) { $curlArgs += @('-H', "Content-Type: $ContentType") }
    foreach ($k in $Headers.Keys) { $curlArgs += @('-H', "$($k): $($Headers[$k])") }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $tmpIn = [System.IO.Path]::GetTempFileName()
        [System.IO.File]::WriteAllText($tmpIn, $Body, (New-Object System.Text.UTF8Encoding $false))
        $curlArgs += @('--data-binary', "@$tmpIn")
    }

    try {
        $code = ("$(& curl.exe @curlArgs 2>$null)").Trim()
        $body = if (Test-Path $tmpOut) { [System.IO.File]::ReadAllText($tmpOut, [System.Text.Encoding]::UTF8) } else { '' }
    }
    finally {
        Remove-Item $tmpOut -Force -ErrorAction SilentlyContinue
        if ($tmpIn) { Remove-Item $tmpIn -Force -ErrorAction SilentlyContinue }
    }

    if ($code -notmatch '^\d+$') { return [pscustomobject]@{ Code = 0; Body = "$body" } }
    return [pscustomobject]@{ Code = [int]$code; Body = "$body" }
}

# Check : vérifie le code HTTP. $Expect = un ou plusieurs codes ("200" ou "200,204").
function Check {
    param(
        [string]$Label, [string]$Expect, [string]$Method, [string]$Path,
        [string]$Auth = '', [string]$Body = '',
        [string]$ContentType = '', [hashtable]$Headers = @{}
    )
    $p = @{ Method = $Method; Path = $Path; Auth = $Auth; ContentType = $ContentType; Headers = $Headers }
    if ($PSBoundParameters.ContainsKey('Body')) { $p['Body'] = $Body }
    $r = Invoke-Api @p

    $expected = $Expect -split ','
    if ($r.Code -eq 0) {
        Write-Host ("  x {0,-56} (pas de réponse — service down ?)" -f $Label) -ForegroundColor Red
        $script:Fail++; $script:Failed += $Label
    }
    elseif ($expected -contains "$($r.Code)") {
        Write-Host ("  v {0,-56} {1}" -f $Label, $r.Code) -ForegroundColor Green
        $script:Pass++
        if ($ShowBody -and $r.Body) { ($r.Body -split "`n" | Select-Object -First 12) | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray } }
    }
    else {
        Write-Host ("  x {0,-56} attendu {1}, reçu {2}" -f $Label, $Expect, $r.Code) -ForegroundColor Red
        $script:Fail++; $script:Failed += "$Label (attendu $Expect, reçu $($r.Code))"
        if ($r.Body) { ($r.Body -split "`n" | Select-Object -First 6) | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray } }
    }
}

# Contains : vérifie le CORPS de la réponse — "répond juste", pas seulement "répond".
function Contains {
    param(
        [string]$Label, [string]$Pattern, [string]$Method, [string]$Path,
        [string]$Auth = '', [string]$Body = '', [string]$ContentType = ''
    )
    $p = @{ Method = $Method; Path = $Path; Auth = $Auth; ContentType = $ContentType }
    if ($PSBoundParameters.ContainsKey('Body')) { $p['Body'] = $Body }
    $r = Invoke-Api @p

    if ($r.Body -match $Pattern) {
        Write-Host ("  v {0,-56} ok" -f $Label) -ForegroundColor Green
        $script:Pass++
        if ($ShowBody) { ($r.Body -split "`n" | Select-Object -First 12) | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray } }
    }
    else {
        Write-Host ("  x {0,-56} motif absent : {1}" -f $Label, $Pattern) -ForegroundColor Red
        $script:Fail++; $script:Failed += "$Label (motif absent)"
        if ($r.Body) { ($r.Body -split "`n" | Select-Object -First 6) | ForEach-Object { Write-Host "      $_" -ForegroundColor DarkGray } }
    }
}

function Note { param([string]$Text) Write-Host "  - $Text" -ForegroundColor Yellow }

$JSON = 'application/json'

# =============================================================================
if (Test-Section 'health' '0. Le système répond') {
    Check 'Gateway joignable (401 attendu sans creds)' '401' 'GET' '/patients'
    Check 'front-service atteint via la Gateway'       '200' 'GET' '/ui/patients' -Auth $UserAuth
    # La Gateway laisse passer /css/** en permitAll, mais front-service exige une auth sur TOUTE requête : le CSS est donc protégé de bout en bout. Le navigateur l'obtient quand même, car il rejoue l'Authorization sur chaque ressource de la page.
    Check 'CSS servi via la Gateway (authentifié)'     '200,304' 'GET' '/css/style.css' -Auth $UserAuth
}

# =============================================================================
if (Test-Section 'security' '1. Authentification — la porte est bien fermée') {
    Check 'sans credentials → 401'           '401' 'GET' '/patients/1'
    Check 'mauvais mot de passe → 401'       '401' 'GET' '/patients/1' -Auth 'medilabo:mauvais'
    Check 'utilisateur inconnu → 401'        '401' 'GET' '/patients/1' -Auth 'pirate:pirate'
    Check 'mot de passe vide → 401'          '401' 'GET' '/patients/1' -Auth 'medilabo:'
    Check 'notes protégées aussi → 401'      '401' 'GET' '/notes?patId=1'
    Check 'assessments protégés aussi → 401' '401' 'GET' '/assessments/1'
    Check "l'UI est protégée aussi → 401"    '401' 'GET' '/ui/patients'

    Note "Les 4 back-ends n'ont AUCUN port publié : docker compose ps le confirme."
    Note 'Le hash BCrypt ne doit jamais être accepté comme mot de passe.'
    Check 'hash BCrypt joué comme mot de passe → 401' '401' 'GET' '/patients/1' `
        -Auth 'medilabo:$2a$10$GzMGhp/NWTujVhv4VyYh9eM.aia95IXMsse7Yl6jUC3DC42/VIinq'
}

# =============================================================================
if (Test-Section 'authz' '2. Autorisation — le moindre privilège') {
    if ($SvcAssessmentAuth -match ':$') {
        Write-Host '  ~ MEDILABO_SVC_ASSESSMENT_PASSWORD non défini — section ignorée.' -ForegroundColor Yellow
        Note 'Pour jouer ces tests : $env:MEDILABO_SVC_ASSESSMENT_PASSWORD = "..."'
        $script:Skip += 6
    }
    else {
        Note 'svc-assessment a le droit de LIRE patients et notes (il en a besoin pour calculer)…'
        Check 'svc-assessment GET /patients/1 → 200' '200' 'GET' '/patients/1' -Auth $SvcAssessmentAuth
        Check 'svc-assessment GET /notes?patId=1 → 200' '200' 'GET' '/notes?patId=1' -Auth $SvcAssessmentAuth

        Note "…mais PAS celui d'écrire. Un compte machine volé ne doit pas muter la base."
        Check 'svc-assessment POST /patients → 403' '403' 'POST' '/patients' -Auth $SvcAssessmentAuth `
            -ContentType $JSON -Body '{"firstName":"Pirate","lastName":"Escalade","dateOfBirth":"1990-01-01","gender":"M"}'
        Check 'svc-assessment PUT /patients/1 → 403' '403' 'PUT' '/patients/1' -Auth $SvcAssessmentAuth `
            -ContentType $JSON -Body '{"firstName":"Pirate","lastName":"Escalade","dateOfBirth":"1990-01-01","gender":"M"}'
        Check 'svc-assessment POST /notes → 403' '403' 'POST' '/notes' -Auth $SvcAssessmentAuth `
            -ContentType $JSON -Body '{"patId":1,"patient":"TestNone","note":"injection"}'

        Note "…et pas non plus celui de consommer les évaluations : c'est le rôle du front."
        Check 'svc-assessment GET /assessments/1 → 403' '403' 'GET' '/assessments/1' -Auth $SvcAssessmentAuth

        Note "403 et non 401 : l'identité est valide, c'est le DROIT qui manque."
    }
}

# =============================================================================
if (Test-Section 'patients' '3. patient-service — lecture & contrat d''erreur') {
    Check 'GET /patients → 200'                   '200' 'GET' '/patients' -Auth $UserAuth
    Contains 'les 4 patients de seed sont là' 'TestNone[\s\S]*TestBorderline[\s\S]*TestInDanger[\s\S]*TestEarlyOnset' `
        'GET' '/patients' -Auth $UserAuth
    Check 'GET /patients/1 → 200'                 '200' 'GET' '/patients/1' -Auth $UserAuth
    Check 'GET /patients/9999 (inexistant) → 404' '404' 'GET' '/patients/9999' -Auth $UserAuth
    Contains '404 au format ProblemDetail RFC 7807' '"status"\s*:\s*404' 'GET' '/patients/9999' -Auth $UserAuth

    Note 'Cas tordus : ce qui fait typiquement sauter un service en 500.'
    Check 'id non numérique (/patients/abc) → 400'   '400' 'GET' '/patients/abc' -Auth $UserAuth
    Check 'id négatif → 404 (pas 500)'               '404' 'GET' '/patients/-1' -Auth $UserAuth
    Check 'id géant (> Integer.MAX) → 404 (pas 500)' '404' 'GET' '/patients/99999999999' -Auth $UserAuth
    Check 'id à virgule (/patients/1.5) → 400'       '400' 'GET' '/patients/1.5' -Auth $UserAuth
    Check 'méthode non supportée (DELETE) → 405|403' '405,403' 'DELETE' '/patients/1' -Auth $UserAuth
}

# =============================================================================
if (Test-Section 'notes' '4. notes-service — contrat de lecture') {
    Check 'GET /notes?patId=4 → 200' '200' 'GET' '/notes?patId=4' -Auth $UserAuth
    Contains 'TestEarlyOnset a bien 4 notes' '("patId"\s*:\s*4[\s\S]*){4}' 'GET' '/notes?patId=4' -Auth $UserAuth

    Note 'Le contrat volontaire : liste vide ≠ erreur.'
    Check 'patId sans aucune note → 200 (pas 404)' '200' 'GET' '/notes?patId=9999' -Auth $UserAuth
    Contains '…et le corps est bien un tableau vide' '^\[\]$' 'GET' '/notes?patId=9999' -Auth $UserAuth

    Note 'Cas tordus sur le paramètre — doivent donner 400, jamais 500.'
    Check 'patId non numérique → 400'           '400' 'GET' '/notes?patId=abc' -Auth $UserAuth
    Check 'patId absent → 400'                  '400' 'GET' '/notes' -Auth $UserAuth
    Check 'patId vide (?patId=) → 400'          '400' 'GET' '/notes?patId=' -Auth $UserAuth
    Check 'patId négatif → 200 + [] (documenté)' '200' 'GET' '/notes?patId=-1' -Auth $UserAuth
    Check "patId débordant l'Integer → 400"     '400' 'GET' '/notes?patId=99999999999' -Auth $UserAuth
    Check 'id Mongo inexistant → 404'           '404' 'GET' '/notes/000000000000000000000000' -Auth $UserAuth
    Check 'id Mongo malformé → 404 (pas 500)'   '404' 'GET' '/notes/pas-un-objectid' -Auth $UserAuth
}

# =============================================================================
if (Test-Section 'assessment' '5. assessment-service — l''oracle des 4 cas canoniques') {
    Note 'LE test de la soutenance : les 4 bandes imposées par le sujet.'

    # Le patient 1 est le seul que la section "mutation" modifie. Si une note "Vertige" a
    # déjà été ajoutée par un run précédent, il est légitimement passé à Borderline — ce
    # n'est pas une régression, c'est un seed sale. On le détecte au lieu de crier au loup.
    $p1 = Invoke-Api -Method 'GET' -Path '/assessments/1' -Auth $UserAuth
    if ($p1.Body -match '"riskBand"\s*:\s*"Borderline"' -and $p1.Body -match 'Vertiges') {
        Write-Host ("  ~ {0,-56} seed modifié par un run précédent" -f 'patient 1 → None') -ForegroundColor Yellow
        Note 'Repartez d''un seed propre : docker compose down -v ; docker compose up -d'
        $script:Skip += 2
    }
    else {
        Contains 'patient 1 → None'          '"riskBand"\s*:\s*"None"' 'GET' '/assessments/1' -Auth $UserAuth
        Contains 'patient 1 → triggerCount 1' '"triggerCount"\s*:\s*1'  'GET' '/assessments/1' -Auth $UserAuth
    }
    Contains 'patient 2 → Borderline'  '"riskBand"\s*:\s*"Borderline"'  'GET' '/assessments/2' -Auth $UserAuth
    Contains 'patient 3 → In Danger'   '"riskBand"\s*:\s*"In Danger"'   'GET' '/assessments/3' -Auth $UserAuth
    Contains 'patient 4 → Early Onset' '"riskBand"\s*:\s*"Early Onset"' 'GET' '/assessments/4' -Auth $UserAuth

    Note 'Le compte de déclencheurs, pas seulement la bande.'
    Contains 'patient 4 → triggerCount 7' '"triggerCount"\s*:\s*7' 'GET' '/assessments/4' -Auth $UserAuth

    Note "L'ORDRE de triggersDetected est chronologique — pas l'ordre de lecture des notes."
    # Les accents sont volontairement remplacés par "." : le test vérifie l'ORDRE, pas
    # l'encodage du terminal. Sinon un souci de console fait échouer un test métier correct.
    Contains 'patient 4 : ordre exact des 7 déclencheurs' `
        '"Anticorps"[\s\S]*"R.action"[\s\S]*"H.moglobine A1C"[\s\S]*"Taille"[\s\S]*"Poids"[\s\S]*"Cholest.rol"[\s\S]*"Vertiges"' `
        'GET' '/assessments/4' -Auth $UserAuth

    Note "L'âge est recalculé à chaque appel, jamais stocké (3NF)."
    Contains "l'enveloppe porte bien un âge" '"age"\s*:\s*\d+' 'GET' '/assessments/4' -Auth $UserAuth

    Note "Cascade d'erreurs depuis les upstreams."
    Check 'patient inexistant → 404 (cascade)' '404' 'GET' '/assessments/9999' -Auth $UserAuth
    Check 'patId non numérique → 400'          '400' 'GET' '/assessments/abc'  -Auth $UserAuth
}

# =============================================================================
if (Test-Section 'edge' '6. Cas hostiles — ce qu''un examinateur essaierait') {
    Note 'Injections : elles doivent être inoffensives, jamais un 500.'
    Check 'SQL injection dans l''id → 400'          '400' 'GET' '/patients/1%20OR%201=1' -Auth $UserAuth
    Check 'SQL injection en paramètre notes → 400'  '400' 'GET' '/notes?patId=1%3B%20DROP%20TABLE%20patient' -Auth $UserAuth
    Check 'NoSQL injection ($ne) → 400'             '400' 'GET' '/notes?patId=%7B%22%24ne%22%3Anull%7D' -Auth $UserAuth
    Check 'path traversal → 400|404'                '400,404' 'GET' '/patients/..%2F..%2Fetc%2Fpasswd' -Auth $UserAuth

    Note 'Corps de requête malformés — 400 attendu, jamais 500.'
    Check 'JSON invalide sur POST /notes → 400'  '400' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON -Body '{"patId":1,"note":'
    Check 'corps vide sur POST /notes → 400'     '400' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON -Body ''
    Check 'champs obligatoires manquants → 400'  '400' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON -Body '{}'
    Contains '…et le 400 liste les champs fautifs' '"errors"' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON -Body '{}'
    Check 'note vide (que des espaces) → 400'    '400' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON `
        -Body '{"patId":1,"patient":"TestNone","note":"   "}'
    Check 'mauvais Content-Type → 415|400'       '415,400' 'POST' '/notes' -Auth $UserAuth -ContentType 'text/plain' `
        -Body 'ceci nest pas du json'
    Check 'Accept non produisible (xml) → 406'   '406' 'GET' '/patients/1' -Auth $UserAuth `
        -Headers @{ Accept = 'application/xml' }

    Note 'Validation métier de patient-service.'
    Check 'genre invalide (X) → 400'              '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"A","lastName":"B","dateOfBirth":"1990-01-01","gender":"X"}'
    Check 'date de naissance dans le futur → 400' '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"A","lastName":"B","dateOfBirth":"2099-01-01","gender":"M"}'
    Check 'naissance il y a 300 ans → 400'        '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"A","lastName":"B","dateOfBirth":"1726-01-01","gender":"M"}'
    Check 'téléphone au mauvais format → 400'     '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"A","lastName":"B","dateOfBirth":"1990-01-01","gender":"M","phone":"06 01 02 03 04"}'
    Check 'prénom vide → 400'                     '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"","lastName":"B","dateOfBirth":"1990-01-01","gender":"M"}'
    Check 'date au mauvais format → 400'          '400' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
        -Body '{"firstName":"A","lastName":"B","dateOfBirth":"31/12/1990","gender":"M"}'

    Note 'Robustesse : gros payload, header Authorization corrompu.'
    # patId=99999 volontairement : si le service accepte (201), la note est persistée. On la
    # rattache donc à un patient inexistant plutôt qu'à TestNone, sinon cette section
    # polluerait le seed que la section "mutation" doit trouver intact.
    $bigNote = '{"patId":99999,"patient":"SmokeBigPayload","note":"' + ('a' * 100000) + '"}'
    Check 'note de 100 000 caractères → 201|400'  '201,400' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON -Body $bigNote
    Check 'Authorization malformé → 401'          '401' 'GET' '/patients/1' -Headers @{ Authorization = 'Basic pas-du-base64' }
    Check 'Authorization vide → 401'              '401' 'GET' '/patients/1' -Headers @{ Authorization = ' ' }
    Check 'schéma Bearer au lieu de Basic → 401'  '401' 'GET' '/patients/1' -Headers @{ Authorization = 'Bearer un-faux-jwt' }
    Check 'route inexistante → 404'               '404' 'GET' '/route-qui-nexiste-pas' -Auth $UserAuth
}

# =============================================================================
if (Test-Section 'mutation' '7. Écriture & recalcul — la preuve de l''absence de cache') {
    Note 'CRUD patient complet : créer, relire, modifier — le chemin nominal, pas que les erreurs.'

    $newPatient = '{"firstName":"Smoke","lastName":"Test","dateOfBirth":"1980-05-15","gender":"F","address":"1 rue du Test","phone":"+33601020304"}'
    $created = Invoke-Api -Method 'POST' -Path '/patients' -Auth $UserAuth -ContentType $JSON -Body $newPatient
    if ($created.Code -eq 201 -or $created.Code -eq 200) {
        Write-Host ("  v {0,-56} {1}" -f 'POST /patients (données valides)', $created.Code) -ForegroundColor Green
        $script:Pass++
    }
    else {
        Write-Host ("  x {0,-56} attendu 200/201, reçu {1}" -f 'POST /patients (données valides)', $created.Code) -ForegroundColor Red
        $script:Fail++; $script:Failed += "POST /patients (reçu $($created.Code))"
    }

    $newId = [regex]::Match($created.Body, '"id"\s*:\s*(\d+)').Groups[1].Value
    if ($newId) {
        Check 'le patient créé est relisible (GET)' '200' 'GET' "/patients/$newId" -Auth $UserAuth
        Contains 'les données créées sont correctes' '"lastName"\s*:\s*"Test"' 'GET' "/patients/$newId" -Auth $UserAuth
        Check 'PUT /patients/{id} (modification) → 200' '200' 'PUT' "/patients/$newId" -Auth $UserAuth -ContentType $JSON `
            -Body '{"firstName":"Smoke","lastName":"Modifie","dateOfBirth":"1980-05-15","gender":"F","address":"2 rue du Test","phone":"+33601020305"}'
        Contains 'la modification est bien persistée' '"lastName"\s*:\s*"Modifie"' 'GET' "/patients/$newId" -Auth $UserAuth
    }
    else {
        Write-Host '  ~ id du patient créé introuvable — CRUD non vérifié' -ForegroundColor Yellow
        $script:Skip += 4
    }

    Note 'Régression : éditer un patient du SEED, pas un patient créé ici.'
    # Le CRUD ci-dessus cree son propre patient avec un telephone francais : il n'a donc jamais exerce le seul chemin qui casse en vrai, editer un patient du seed dont le telephone est en +1. Ce cas rejoue une modification d'adresse seule, telephone relu et renvoye tel quel comme le fait le formulaire : l'angle mort qui a laisse passer un 500 en production. Seule l'adresse est touchee : ni la bande de risque ni les notes n'en dependent, le seed reste utilisable par la verification de recalcul qui suit.
    $seed2 = Invoke-Api -Method 'GET' -Path '/patients/2' -Auth $UserAuth
    $seed2Phone = [regex]::Match($seed2.Body, '"phone"\s*:\s*"([^"]*)"').Groups[1].Value

    if (-not $seed2Phone) {
        Write-Host ("  ~ {0,-56} seed introuvable ou sans téléphone" -f "édition d'un patient du seed (adresse seule)") -ForegroundColor Yellow
        $script:Skip += 2
    }
    else {
        Write-Host "      téléphone du seed relu : $seed2Phone" -ForegroundColor DarkGray
        Check 'PUT patient du seed, adresse seule modifiée → 200' '200' 'PUT' '/patients/2' -Auth $UserAuth -ContentType $JSON `
            -Body ('{"firstName":"Test","lastName":"TestBorderline","dateOfBirth":"1945-06-24","gender":"M","address":"2 High Street","phone":"' + $seed2Phone + '"}')
        Contains '…et le téléphone du seed est resté valide' ('"phone"\s*:\s*"' + [regex]::Escape($seed2Phone) + '"') 'GET' '/patients/2' -Auth $UserAuth
    }

    Note 'Chaque indicatif de PhoneCountry (front) doit être accepté par patient-service.'
    # Garde-fou anti-desynchronisation : la liste des indicatifs est dupliquee entre PhoneCountry (front-service) et le regex de PatientDTO (patient-service). Ajouter une entree d'un cote sans l'autre produit un E.164 que le back rejette en 400, rendu en 500 par le front qui n'intercepte pas l'erreur. Un numero par indicatif supporte suffit a detecter la desynchronisation des le prochain ajout.
    foreach ($e164 in @('+33601020304', '+32470123456', '+41791234567', '+447911123456', '+393123456789', '+12003334444')) {
        Check "téléphone $e164 accepté → 201" '201' 'POST' '/patients' -Auth $UserAuth -ContentType $JSON `
            -Body ('{"firstName":"Smoke","lastName":"Indicatif","dateOfBirth":"1980-05-15","gender":"F","phone":"' + $e164 + '"}')
    }

    Note 'FR-9 : ajouter une note doit changer la bande IMMÉDIATEMENT, sans cache.'

    $before = Invoke-Api -Method 'GET' -Path '/assessments/1' -Auth $UserAuth
    $bm = [regex]::Match($before.Body, '"riskBand"\s*:\s*"([^"]*)"')
    $bandBefore = if ($bm.Success) { $bm.Groups[1].Value } else { '?' }
    Write-Host "      avant : $bandBefore" -ForegroundColor DarkGray

    # L'état initial est vérifié AVANT d'écrire : sur un seed propre TestNone est à None.
    # S'il est déjà Borderline (run précédent), ajouter une note ne pourrait plus rien faire basculer, on s'abstient donc d'écrire, pour ne pas polluer davantage un seed déjà sale.
    if ($bandBefore -ne 'None') {
        Write-Host ("  ~ {0,-56} non concluant : départ {1}, pas None" -f 'recalcul immédiat, sans cache', $bandBefore) -ForegroundColor Yellow
        Note 'Seed déjà modifié : docker compose down -v ; docker compose up -d, puis relancer.'
        Note 'Aucune note ajoutée — le seed n''est pas pollué davantage.'
        $script:Skip += 2
    }
    else {
        Check "POST d'une note 'Vertige' sur TestNone → 201" '201' 'POST' '/notes' -Auth $UserAuth -ContentType $JSON `
            -Body '{"patId":1,"patient":"TestNone","note":"Le patient se plaint de Vertige depuis peu"}'

        $after = Invoke-Api -Method 'GET' -Path '/assessments/1' -Auth $UserAuth
        $am = [regex]::Match($after.Body, '"riskBand"\s*:\s*"([^"]*)"')
        $bandAfter = if ($am.Success) { $am.Groups[1].Value } else { '?' }
        Write-Host "      après : $bandAfter" -ForegroundColor DarkGray

        if ($bandAfter -eq 'Borderline') {
            Write-Host ("  v {0,-56} {1} → {2}" -f 'recalcul immédiat, sans cache', $bandBefore, $bandAfter) -ForegroundColor Green
            $script:Pass++
        }
        else {
            Write-Host ("  x {0,-56} {1} → {2}, Borderline attendu" -f 'recalcul immédiat, sans cache', $bandBefore, $bandAfter) -ForegroundColor Red
            $script:Fail++; $script:Failed += "recalcul du risque après ajout de note ($bandBefore → $bandAfter)"
        }
    }

    Note "Le matching ignore casse ET accents — 'vertige' minuscule sans accent compte aussi."
    Note "Limite connue et assumée : pas de gestion de la négation ('pas de vertiges' compte)."

    Write-Host ''
    Write-Host '  État modifié. Pour revenir au seed : docker compose down -v && docker compose up -d' -ForegroundColor Yellow
}

# =============================================================================
Write-Host ''
Write-Host '─────────────────────────────────────────────' -ForegroundColor White
$skipTxt = if ($script:Skip -gt 0) { ", $($script:Skip) ignorés" } else { '' }
if ($script:Fail -eq 0) {
    Write-Host "  $($script:Pass) réussis, 0 échec$skipTxt" -ForegroundColor Green
    Write-Host ''
    exit 0
}
else {
    Write-Host "  $($script:Pass) réussis, $($script:Fail) ÉCHECS$skipTxt" -ForegroundColor Red
    foreach ($t in $script:Failed) { Write-Host "    x $t" -ForegroundColor Red }
    Write-Host ''
    exit 1
}
