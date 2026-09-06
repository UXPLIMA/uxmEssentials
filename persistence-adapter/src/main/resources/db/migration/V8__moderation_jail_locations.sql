-- Adds the writable jail-location store behind /setjail and /deljail. Jails were
-- config-only and read-only: an operator had to edit moderation.conf by hand to
-- define one and could never remove one in-game. A row here is a jail saved at a
-- staff member's position, keyed by its canonical lowercase name, merged with the
-- config `jails` list behind the combined directory so /jail validates against both
-- and the sanction adapter resolves a stored jail's location (store first).
--
-- The location is the warps shape verbatim (V1 `warps`): the world uid as canonical
-- 36-char text plus the operator-facing world name, the coordinates as DOUBLE
-- PRECISION and the look angles as REAL, so the column types are identical on every
-- backend. defined_by is the actor uuid (NULL for a console actor); created_at is
-- epoch milliseconds in a BIGINT, mirroring every other instant in the schema.
--
-- Same portability contract as V1-V7: a plain CREATE TABLE in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept, with no
-- dialect-specific clause. jOOQ's DDLDatabase parses this file alongside V1-V7 at
-- build time, so the generated ModerationJailLocations table and record appear.

CREATE TABLE moderation_jail_locations (
    name        VARCHAR(64)      NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    defined_by  VARCHAR(36),
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_moderation_jail_locations PRIMARY KEY (name)
);
