-- Persists the pre-warmed random-teleport pool (ADR 0010) so the per-world safe-location queue
-- survives a restart instead of cold-starting empty. Until now the queue lived only in memory: the
-- first /rtp per world after a reboot triggered a full 40-attempt search storm because nothing on
-- disk kept the queue warm. This table is that durable mirror. A bounded set of pre-validated
-- columns per world that the module re-probes and re-validates on enable to refill the in-memory
-- queue before the first player asks.
--
-- One row is one validated column: the world uuid as canonical 36-char text (keyed the same way as
-- teleport_spawns/npc/warps), the block x/z as INTEGER, the biome the column validated in (nullable,
-- reserved for the P5 per-biome pool slice. Written now, read there), and validated_at as epoch
-- milliseconds in a BIGINT, mirroring every other instant in the schema (created_at across V1-V68).
-- The column is never served blindly on restart: only the x/z are trusted as a known-good sample, and
-- the landing Y plus the full safety check are recomputed live off an async chunk probe before the
-- column re-enters the servable queue.
--
-- id is a synthetic BIGINT primary key assigned application-side with the max(id)+1 idiom the
-- moderation warn/sanction history already uses (V5/V11), so the schema needs no dialect-specific
-- auto-increment clause. idx_rtp_pool_world backs every access, the per-world load on enable, the
-- per-world count, and the per-world cap eviction that bounds the pool's growth.
--
-- Same portability contract as V1-V68: a plain CREATE TABLE in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses
-- this file alongside the rest at build time, so the generated RtpPool table and record appear with no
-- extra configuration.

CREATE TABLE rtp_pool (
    id            BIGINT       NOT NULL,
    world_uuid    VARCHAR(36)  NOT NULL,
    x             INTEGER      NOT NULL,
    z             INTEGER      NOT NULL,
    biome         VARCHAR(128),
    validated_at  BIGINT       NOT NULL,
    CONSTRAINT pk_rtp_pool PRIMARY KEY (id)
);

CREATE INDEX idx_rtp_pool_world ON rtp_pool (world_uuid);
