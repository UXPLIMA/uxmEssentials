-- Promotes the spawn directory behind /spawn from session-only memory to durable
-- storage. Until now operator spawns lived in an in-memory map and a /setspawn was
-- lost on restart, with a single per-world default and a config-loaded mirror map;
-- these tables make the per-world spawn, the global main spawn, the named spawns and
-- the spawn mirrors all survive a restart and a world rollback (they are never
-- transient PDC stamps).
--
-- Four tables, each the warps shape (V1 `warps`) for the location columns: the world
-- uid as canonical 36-char text plus the operator-facing world name, the coordinates
-- as DOUBLE PRECISION and the look angles as REAL, so the column types are identical
-- on every backend. created_at is epoch milliseconds in a BIGINT, mirroring every
-- other instant in the schema.
--
--   teleport_spawns. One row per world, keyed by the world uid; the spawn
--                            /setspawn writes and /removespawn clears.
--   teleport_main_spawn. The single global main spawn /setmainspawn writes, a
--                            singleton row pinned at id = 0 and upserted on it (the
--                            V9 home_main pattern, global rather than per-owner);
--                            used when a world has no spawn of its own.
--   teleport_named_spawns. Named spawns (/setspawn <name>, /spawn <name>), keyed by
--                            the canonical lowercase name.
--   teleport_spawn_mirrors: per-world /spawn redirects, keyed by the source world.
--
-- Same portability contract as V1-V9: a plain CREATE TABLE in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept, with no
-- dialect-specific clause. jOOQ's DDLDatabase parses this file alongside V1-V9 at
-- build time, so the generated TeleportSpawns, TeleportMainSpawn, TeleportNamedSpawns
-- and TeleportSpawnMirrors tables and records appear.

CREATE TABLE teleport_spawns (
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_teleport_spawns PRIMARY KEY (world)
);

CREATE TABLE teleport_main_spawn (
    id          INTEGER          NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_teleport_main_spawn PRIMARY KEY (id)
);

CREATE TABLE teleport_named_spawns (
    name        VARCHAR(64)      NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_teleport_named_spawns PRIMARY KEY (name)
);

-- The mirror is the SpawnMirror domain record verbatim: a source world uid redirected
-- to a target world uid. The world names are not stored. They are resolved from the
-- live world lookup at read time, the same way the moderation gate resolves a world,
-- so a renamed world folder never leaves a stale name behind.
CREATE TABLE teleport_spawn_mirrors (
    source_world  VARCHAR(36)  NOT NULL,
    target_world  VARCHAR(36)  NOT NULL,
    created_at    BIGINT       NOT NULL,
    CONSTRAINT pk_teleport_spawn_mirrors PRIMARY KEY (source_world)
);
