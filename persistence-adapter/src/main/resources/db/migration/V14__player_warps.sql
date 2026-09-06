-- Adds the durable store behind /setpwarp and /pwarp: player-owned warps keyed (owner, name).
-- Player-warps mirror the server warps shape (V1) but are owned per player like homes, so the row
-- carries an owner column and the primary key is the (owner, name) composite. Two players may each
-- keep a warp named "base". A public flag decides whether non-owners may teleport to the warp.
--
-- The location columns match homes/warps verbatim: owner and world are the canonical 36-char UUID
-- text, world_name the operator-facing name, x/y/z DOUBLE PRECISION, yaw/pitch REAL, created_at epoch
-- milliseconds in a BIGINT. Identical column shapes on every backend so a player-warp round-trips the
-- same in SQLite, MySQL/MariaDB and PostgreSQL.
--
-- is_public is stored as an INT (0/1) rather than a BOOLEAN for the same portability reason BIGINT
-- carries the instant: the subset all three backends accept without a dialect-specific type. 1 means
-- the warp is public (any player may use it); 0 means private (owner only).
--
-- Same portability contract as V1-V13: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's
-- DDLDatabase parses this file alongside V1-V13 at build time, so the generated PlayerWarps table and
-- record appear with no extra configuration.

CREATE TABLE player_warps (
    owner       VARCHAR(36)      NOT NULL,
    name        VARCHAR(64)      NOT NULL,
    world       VARCHAR(36)      NOT NULL,
    world_name  VARCHAR(128)     NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    z           DOUBLE PRECISION NOT NULL,
    yaw         REAL             NOT NULL,
    pitch       REAL             NOT NULL,
    is_public   INT              NOT NULL,
    created_at  BIGINT           NOT NULL,
    CONSTRAINT pk_player_warps PRIMARY KEY (owner, name)
);

CREATE INDEX idx_player_warps_owner ON player_warps (owner);

CREATE INDEX idx_player_warps_public ON player_warps (is_public);
