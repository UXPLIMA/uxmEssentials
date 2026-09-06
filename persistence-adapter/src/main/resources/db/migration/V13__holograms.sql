-- Adds the durable store behind /hologram: named, world-placed, multi-line text displays
-- that survive a restart. A hologram is a native TextDisplay entity at runtime, but the
-- entity is ephemeral (despawned on disable, re-spawned on enable), so the source of truth
-- is here, never the world save. A hologram comes back exactly as configured after a crash
-- or a world rollback.
--
-- The name row mirrors warps/teleport_spawns (V1): world is the world uuid as canonical
-- 36-char text, world_name the operator-facing name, x/y/z DOUBLE PRECISION, yaw/pitch REAL,
-- created_at epoch milliseconds in a BIGINT. Identical column shapes on every backend so a
-- hologram round-trips the same in SQLite, MySQL/MariaDB and PostgreSQL.
--
-- The lines live in a SEPARATE child table keyed (hologram, idx), NOT an opaque JSON blob:
-- the architecture persistence invariant is that every queryable fact is a first-class column,
-- so a line is a row that can be selected, ordered and edited in place. idx is the 0-based
-- render order (top-down); text is the raw MiniMessage source (a TextDisplay renders it). The
-- foreign-key-like relationship is enforced by the application (it rewrites the whole line set
-- on every save), kept dialect-portable by avoiding an ON DELETE CASCADE clause SQLite gates
-- behind a pragma: the delete removes the lines and the row in one transaction.
--
-- Same portability contract as V1-V12: a plain CREATE TABLE / CREATE INDEX in the subset
-- SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific
-- clause. jOOQ's DDLDatabase parses this file alongside V1-V12 at build time, so the generated
-- Holograms and HologramLines tables and records appear with no extra configuration.

CREATE TABLE holograms (
    name        VARCHAR(64)      NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_holograms PRIMARY KEY (name)
);

CREATE TABLE hologram_lines (
    hologram  VARCHAR(64)  NOT NULL,
    idx       INT          NOT NULL,
    text      VARCHAR(512) NOT NULL,
    CONSTRAINT pk_hologram_lines PRIMARY KEY (hologram, idx)
);

CREATE INDEX idx_hologram_lines_hologram ON hologram_lines (hologram);
