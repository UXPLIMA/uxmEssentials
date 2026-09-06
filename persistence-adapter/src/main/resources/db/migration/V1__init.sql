-- Baseline schema for the homes and warps bounded contexts.
--
-- The DDL is kept to the portable subset every supported backend understands:
-- SQLite (the default, single-node servers), MySQL/MariaDB and PostgreSQL. UUIDs
-- are stored as their canonical 36-character text so the column shape is identical
-- on a backend without a native uuid type; timestamps are epoch milliseconds in a
-- BIGINT so there is no dialect-specific datetime handling. Every queryable fact
-- (owner, name, world, coordinates, cost, required permission) is a first-class
-- column: there are no opaque JSON blobs (architecture persistence invariant).
--
-- jOOQ's code generator parses exactly this file at build time through the Flyway
-- DDLDatabase, so the generated classes always match the migration that creates
-- the tables at runtime.

CREATE TABLE homes (
    owner       VARCHAR(36)      NOT NULL,
    name        VARCHAR(64)      NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_homes PRIMARY KEY (owner, name)
);

CREATE INDEX idx_homes_owner ON homes (owner);

CREATE TABLE warps (
    name                VARCHAR(64)      NOT NULL,
    world               VARCHAR(36)      NOT NULL,
    world_name          VARCHAR(128)     NOT NULL,
    x                   DOUBLE PRECISION NOT NULL,
    y                   DOUBLE PRECISION NOT NULL,
    z                   DOUBLE PRECISION NOT NULL,
    yaw                 REAL             NOT NULL,
    pitch               REAL             NOT NULL,
    owner               VARCHAR(36)      NOT NULL,
    created_at          BIGINT           NOT NULL,
    cost                DECIMAL(20, 4),
    required_permission VARCHAR(128),
    CONSTRAINT pk_warps PRIMARY KEY (name)
);
