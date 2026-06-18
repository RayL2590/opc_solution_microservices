-- 3NF schema for patient-service (NFR-D1 evaluation artifact).
--
-- Normalization rationale (referenced by README NFR-C1 3NF justification):
--   * 1NF: every column holds an atomic, single-valued attribute.
--   * 2NF: trivially holds — surrogate single-column PK, no partial dependency possible.
--   * 3NF: no transitive dependency — no derived column (e.g. age is computed at read time,
--          never stored), every non-key column depends on `id` and only on `id`.
--   * One row per patient. Multi-valued attributes (e.g. multiple phones / addresses) would
--          live in a separate table; v1 deliberately stores at most one of each.
--
-- Validated at startup: spring.jpa.hibernate.ddl-auto=validate. Any drift between this
-- schema and the JPA entity fails fast at boot.
--
-- DROP-then-CREATE pattern: every fresh boot recreates the table so data.sql can re-seed
-- the four canonical patients with deterministic ids (NFR-D2 "every fresh boot"). For
-- production deployment, override `spring.sql.init.mode=never` (out of v1 scope).

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
