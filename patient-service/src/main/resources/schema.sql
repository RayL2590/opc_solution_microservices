-- Schéma en 3NF pour patient-service.
--
-- Pourquoi c'est en 3NF (repris dans la justification du README) :
--   * 1NF : chaque colonne = un attribut atomique, une seule valeur.
--   * 2NF : acquis d'office, clé primaire sur une seule colonne donc pas de dépendance partielle possible.
--   * 3NF : pas de dépendance transitive - rien de dérivé stocké (l'âge par exemple se calcule à la lecture, jamais en base), chaque colonne hors clé ne dépend que de `id`.
--   * Une ligne par patient. Si un jour on doit gérer plusieurs téléphones ou adresses par  patient, ça ira dans une table à part ; pour la v1 une seule valeur de chaque suffit.
--
-- spring.jpa.hibernate.ddl-auto=validate vérifie ce schéma au démarrage : le moindre écart avec l'entité JPA et l'appli ne démarre pas.
--
-- DROP puis CREATE parce qu'à chaque démarrage à froid on veut repartir propre, pour que data.sql réinjecte les quatre patients de référence avec des ids stables. En prod il faudra surcharger `spring.sql.init.mode=never` — hors périmètre de la v1.

DROP TABLE IF EXISTS patient;

CREATE TABLE patient (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        VARCHAR(1)   NOT NULL,
    address       VARCHAR(255) NULL,
    phone         VARCHAR(20)  NULL,
    PRIMARY KEY (id)
);
