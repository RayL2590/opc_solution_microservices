# Déploiement en production — VPS + HTTPS

> Récapitulatif de la mise en production de MédiLabo sur `microservices.ryan-loche.fr`.
> Cible : un VPS Debian qui héberge **déjà** une douzaine d'autres sites derrière Apache.
> Le projet reste un projet de démo (données de test, pas de données réelles).

---

## 1. Architecture retenue

```
Navigateur ──HTTPS(443)──> Apache (reverse proxy, TLS Let's Encrypt)
                              │
                              └──HTTP──> 127.0.0.1:8080  gateway-service (conteneur)
                                              │
                                              ├──> patient-service:8081 ──> MySQL
                                              ├──> notes-service:8082   ──> MongoDB
                                              ├──> assessment-service:8083
                                              └──> front-service:8084
```

**Le TLS termine chez Apache, pas dans Java.** Trois raisons :

- Apache occupait déjà les ports 80/443 pour les autres sites — y ajouter Caddy ou Traefik aurait cassé l'existant.
- Aucun keystore PKCS12 à gérer côté Spring, certbot fait tout.
- Un seul certificat : le Gateway est le seul point d'entrée, les autres services n'ont aucun `ports:` et ne sont joignables que depuis le réseau Docker interne.

---

## 2. Ce qui a changé dans le code

Trois modifications, toutes rétrocompatibles avec le `docker compose up` local.

### 2.1 — `docker-compose.prod.yml` (nouveau)

Override de prod, lu **uniquement** si on le passe explicitement en `-f`. Deux réglages :

| Réglage | Pourquoi |
|---|---|
| `ports: !override ["127.0.0.1:8080:8080"]` | Apache devient le seul chemin d'entrée |
| `MEDILABO_PUBLIC_HOST` / `MEDILABO_PUBLIC_PROTO` | Le front doit construire ses URLs vers le domaine public en HTTPS |

**Piège rencontré : `!override` est obligatoire.** Compose **concatène** les listes de `ports` entre fichiers au lieu de les remplacer. Sans le tag, le `"8080:8080"` du fichier de base
(donc `0.0.0.0`) survit à côté du binding loopback, et le Gateway reste joignable en HTTP nu depuis Internet — le TLS d'Apache contourné.

Et surtout **pas `!reset`** : il supprime la clé entière, plus aucun port n'est publié.

Vérification qui tranche (un seul mapping, avec `host_ip`) :

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config 2>/dev/null \
  | grep -E "published|host_ip"
```

> À savoir : les règles iptables que Docker installe passent **avant** celles d'UFW.
> Un port publié en `0.0.0.0` est exposé même si le firewall le refuse — d'où le binding explicite sur la loopback.

### 2.2 — `gateway-service/.../application-docker.yml`

Les deux en-têtes `X-Forwarded-*` de la route `front` étaient codés en dur sur `localhost:8080` / `http`. Ils sont désormais paramétrés :

```yaml
- AddRequestHeader=X-Forwarded-Host, ${MEDILABO_PUBLIC_HOST:localhost:8080}
- AddRequestHeader=X-Forwarded-Proto, ${MEDILABO_PUBLIC_PROTO:http}
```

Les défauts reproduisent exactement le comportement local d'avant. Sans ça, le front renvoie des redirections vers `http://localhost:8080/ui/...` que le navigateur ne peut pas suivre.

Ça marche parce que `front-service` a déjà `server.forward-headers-strategy=framework`.

### 2.3 — `spring.mvc.servlet.load-on-startup=1`

Ajouté dans `application.properties` de **notes**, **patient**, **assessment** et **front** (pas le gateway : WebFlux, il n'a pas de servlet).

Par défaut Spring instancie le DispatcherServlet à la première requête (~800 ms). La fiche patient cumulait ce coût sur toute la chaîne (front → gateway → patient + notes + assessment) et dépassait le timeout : **500 au premier accès, OK au rechargement**.

Preuve que le correctif est actif — le thread doit être `[main]`, pas `[nio-xxxx-exec-1]` :

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs notes-service 2>/dev/null \
  | grep -i dispatcher
```

> Le JIT de la JVM reste à chaud : ~7 s au tout premier appel après un rebuild, ~1,3 s ensuite.
> **Avant une démo, faire un appel de préchauffage** (voir §6).

---

## 3. Configuration Apache

Fichier `/etc/apache2/sites-available/microservices.ryan-loche.fr.conf` :

```apache
<VirtualHost *:80>
    ServerName microservices.ryan-loche.fr

    ProxyPreserveHost On
    ProxyPass        / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    ErrorLog  ${APACHE_LOG_DIR}/microservices_error.log
    CustomLog ${APACHE_LOG_DIR}/microservices_access.log combined
</VirtualHost>
```

Différence avec les autres vhosts du serveur : **ni `DocumentRoot` ni `<Directory>`** — rien n'est servi depuis le disque, tout part vers le conteneur.

Ne pas écrire la redirection HTTPS à la main : certbot l'ajoute lui-même et génère le `-le-ssl.conf`.

```bash
a2enmod proxy proxy_http headers
a2ensite microservices.ryan-loche.fr.conf
apache2ctl configtest && systemctl reload apache2   # configtest AVANT le reload
```

> `configtest` d'abord : une faute de frappe ferait tomber les douze autres sites du VPS.

---

## 4. Fichiers à déployer

Emplacement : **`/opt/medilabo`**, délibérément **hors de `/var/www/html`** — le vhost ne sert aucun fichier, et un `.env` dans une arborescence web peut devenir téléchargeable sur une mauvaise config.

```
docker-compose.yml
docker-compose.prod.yml
docker/                       # mongo-init.js (seed Mongo)
.env  .env.docker             # git-ignorés -> à créer sur le serveur, chmod 600
gateway-service/    -> Dockerfile + pom.xml + src/
patient-service/    -> Dockerfile + pom.xml + src/
notes-service/      -> Dockerfile + pom.xml + src/
assessment-service/ -> Dockerfile + pom.xml + src/
front-service/      -> Dockerfile + pom.xml + src/
```

**À ne pas envoyer :** `target/` (191 Mo, inutile — les Dockerfiles sont multi-stage et lancent `mvn package` dans le conteneur), `.git/`, `integration-tests/`, `Documentation/`,
`docs/`, `_bmad*`, `.claude/`, `mvnw*`, `.mvn/`, `HELP.md`.

→ ~1 Mo au lieu de ~250 Mo. Dans WinSCP, mettre `target/; .git/; tmp/; output/` en masque d'exclusion.

```bash
chmod 600 /opt/medilabo/.env /opt/medilabo/.env.docker   # sinon lisibles par www-data
```

---

## 5. Séquence de premier déploiement

```bash
# 1. DNS — doit résoudre en A (IPv4) ET AAAA vers le VPS
nslookup microservices.ryan-loche.fr     # depuis Windows (résolveur externe) getent hosts microservices.ryan-loche.fr # depuis le VPS ; "dig" n'est pas installé

# 2. Sanity check de la config fusionnée
cd /opt/medilabo
docker compose -f docker-compose.yml -f docker-compose.prod.yml config >/dev/null

# 3. Build + démarrage (premier build : 5-10 min)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# 4. Le Gateway répond-il en local ?
curl -I http://127.0.0.1:8080/                       # 401 attendu (Basic Auth) = OK
curl -i -u USER:PASS http://127.0.0.1:8080/          # 302 vers /ui/patients

# 5. Apache proxifie-t-il ?
curl -I http://microservices.ryan-loche.fr/          # 401 attendu

# 6. Certificat (répondre OUI à la redirection HTTP->HTTPS)
certbot --apache -d microservices.ryan-loche.fr
```

> **Un `401` sur `/` est le résultat correct**, pas une erreur : le Gateway protège tout par HTTP Basic. C'est `-u USER:PASS` qui donne le `302`.

---

## 6. Commandes du quotidien

```bash
cd /opt/medilabo
export C="-f docker-compose.yml -f docker-compose.prod.yml"   # raccourci

docker compose $C ps 2>/dev/null              # état des conteneurs
docker compose $C logs -f SERVICE 2>/dev/null # suivre un service
docker compose $C restart SERVICE             # redémarrer sans rebuild
docker compose $C up -d --build               # après modif de code/properties
docker compose $C down                        # arrêt, volumes CONSERVÉS
```

> `2>/dev/null` masque les warnings `"GzMGhp" variable is not set` : ce sont des fragments de hash BCrypt que Compose prend pour des variables. **Ils sont normaux** — c'est précisément la raison d'être de `.env.docker` et de son échappement `$$`.

**Préchauffage avant une démo** (élimine la latence JIT du premier appel) :

```bash
curl -s -o /dev/null -u USER:PASS https://microservices.ryan-loche.fr/ui/patients/1
```

### ⚠️ `down -v` détruit les données

Le `-v` supprime les volumes MySQL et MongoDB → **perte du seed**. Ne l'utiliser que volontairement (ex. réinitialiser après un échec d'init MySQL, ou changer le mot de passe
MySQL — il n'est pris en compte qu'à la toute première initialisation de la base).

Un simple changement de code ne demande **jamais** de `-v` :

```bash
docker compose $C up -d --build    # rebuild, volumes intacts
```

---

## 7. Environnement local : inchangé

Les trois modifications sont rétrocompatibles. La démo en dev se lance exactement comme avant :

```powershell
docker compose up -d --build        # sans -f docker-compose.prod.yml
```

→ **http://localhost:8080**

- `docker-compose.prod.yml` n'est lu que s'il est passé en `-f`
- les `${MEDILABO_PUBLIC_*}` retombent sur leurs défauts `localhost:8080` / `http`
- `load-on-startup` ne change que le *moment* de l'initialisation

Vérification (doit afficher `published: "8080"` **sans** `host_ip`) :

```bash
docker compose config 2>/dev/null | grep -E "published|host_ip"
```

---

## 8. Incidents rencontrés — et leur cause réelle

| Symptôme | Cause | Correctif |
|---|---|---|
| `mysql exited (1)`, `InnoDB Error number 28` | **Disque plein** (0 octet libre), pas la config | Nettoyage §9 puis `down -v` (base avortée) |
| Fiche patient en 500, OK au rechargement | DispatcherServlet initialisé paresseusement | `load-on-startup=1` (§2.3) |
| `curl /notes/patient/1` → 500 | **URL inventée** : la vraie route est `/notes?patId=1` | — (faux positif de diagnostic) |
| Warnings `"GzMGhp" is not set` | Fragments de hash BCrypt lus comme variables | Aucun — comportement normal |

> Leçon de méthode : deux des pistes suivies (mapping Mongo, mot de passe de service manquant) étaient fausses. **C'est la stacktrace qui a tranché**, pas les hypothèses.
> Réflexe : `docker compose $C logs SERVICE | grep -A30 -i exception`.

---

## 9. Santé du VPS

Le disque était **plein à 100 %** (20 Go), ce qui bloquait aussi les autres sites.

Causes : `logrotate` **n'était pas installé** (Apache avait accumulé 3 Go de logs), `journald` sans plafond (2 Go), `btmp` à 582 Mo (tentatives SSH en force brute).

```bash
df -h /                              # espace disque
docker system df                     # ce que Docker occupe
du -sh /var/log/* | sort -h | tail   # les gros postes de logs

# Nettoyage sans risque
docker builder prune -af             # cache de build ; les images restent
journalctl --vacuum-size=200M
: > /var/log/btmp                    # vider, pas rm (garde le fichier et ses droits)
/usr/sbin/logrotate -f /etc/logrotate.d/apache2
```

Corrections durables appliquées : `apt install logrotate` (timer systemd actif) et
`SystemMaxUse=500M` dans `/etc/systemd/journald.conf`.

> Prévoir **≥ 3 Go libres** avant un `up --build` : cinq builds Maven, c'est plusieurs Go de couches intermédiaires.
>
> Piste si `btmp` regonfle : `fail2ban` bannit les IP après quelques échecs SSH.

---

## 10. Certificat TLS

Renouvellement **automatique** (tâche programmée installée par certbot).

```bash
certbot certificates                 # état et date d'expiration
certbot renew --dry-run              # tester le renouvellement sans l'exécuter
```

Fichiers : `/etc/letsencrypt/live/microservices.ryan-loche.fr/`
Vhost TLS généré : `/etc/apache2/sites-available/microservices.ryan-loche.fr-le-ssl.conf`

---

## 11. Points ouverts

- **Secrets** — le projet tourne avec les identifiants de démo, et le mot de passe MySQL est présent dans l'historique git (commit `a41da6b`). Acceptable ici : données de test, aucun enjeu réel. À rotationner si le projet devait servir à autre chose.
- **mTLS entre services** — non implémenté (≈3-5 j : CA interne, cert par service, keystore + truststore par conteneur, tests à adapter). Le trafic inter-services ne quitte jamais le réseau Docker de l'hôte, ce qui justifie d'avoir priorisé le TLS externe, là où les identifiants Basic transitent réellement sur Internet.
- **Écarts local/prod** — la prod tourne sur le code déployé au dernier build. Toute modification locale nécessite un renvoi des fichiers **et** un `up -d --build`.
