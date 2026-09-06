-- Adds the durable store behind /npc: named, world-placed fake-player NPCs that survive a
-- restart. An NPC has no real entity at runtime. It is rendered to each viewer entirely with
-- packets (a player-info entry plus a spawn-player packet, then the entry is hidden so it never
-- shows in the tab list), so the source of truth is here, never the world save. After a crash or
-- a world rollback an NPC comes back exactly as configured: same place, same skin, same click
-- command.
--
-- The row mirrors holograms/warps/teleport_spawns (V13/V1): world is the world uuid as canonical
-- 36-char text, world_name the operator-facing name, x/y/z DOUBLE PRECISION, yaw/pitch REAL,
-- created_at epoch milliseconds in a BIGINT. Identical column shapes on every backend so an NPC
-- round-trips the same in SQLite, MySQL/MariaDB and PostgreSQL.
--
-- The skin is the two raw protocol strings: skin_texture is the base64 "textures" property value,
-- skin_signature its Yggdrasil signature. Both are nullable. A NULL pair is the default (Steve)
-- skin. They are queryable first-class columns (not an opaque blob): they carry no internal
-- structure SQL needs to filter on, but storing them as plain text columns keeps the schema uniform
-- with the rest (no binary types) and lets an operator inspect or back them up directly. click_command
-- is the command run when a player clicks the NPC, nullable for "no action", the interaction that
-- runs it is wired in a later sub-phase, the binding is stored now.
--
-- Same portability contract as V1-V37: a plain CREATE TABLE in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses
-- this file alongside V1-V37 at build time, so the generated Npc table and record appear with no extra
-- configuration.

CREATE TABLE npc (
    name            VARCHAR(64)      NOT NULL,
    world           VARCHAR(36)      NOT NULL,
    world_name      VARCHAR(128)     NOT NULL,
    x               DOUBLE PRECISION NOT NULL,
    y               DOUBLE PRECISION NOT NULL,
    z               DOUBLE PRECISION NOT NULL,
    yaw             REAL             NOT NULL,
    pitch           REAL             NOT NULL,
    skin_texture    TEXT,
    skin_signature  TEXT,
    click_command   VARCHAR(256),
    created_at      BIGINT           NOT NULL,
    CONSTRAINT pk_npc PRIMARY KEY (name)
);
