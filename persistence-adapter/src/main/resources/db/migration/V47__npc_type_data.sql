-- Adds the per-entity-type appearance metadata of an NPC (V38-V46 are the name row, look toggle, equipment/glow,
-- the action chain, the entity type, the widened equipment payload and the pose/scale columns). A mob NPC can now
-- carry type-specific appearance data. A baby flag, a slime size, a creeper's charged state, a villager's
-- profession/type/level, the per-type metadata surface.
--
-- The metadata lives in a SEPARATE child table keyed (npc_name, data_key), NOT an opaque JSON blob: the
-- architecture persistence invariant is that every queryable fact is a first-class column, so a datum is a row
-- that can be selected and edited in place. data_key is the metadata key (baby, size, charged, villager_*) and
-- data_value the raw operator value the render adapter parses per key; both are plain strings, the same shape the
-- domain carries, so the adapter resolves them to packets without the domain ever touching Bukkit. The
-- foreign-key-like relationship is enforced by the application (it rewrites the whole metadata set on every save)
-- and kept dialect-portable by avoiding an ON DELETE CASCADE clause SQLite gates behind a pragma, the delete
-- removes the metadata and the row in one transaction, mirroring npc_action (V41) and hologram_lines (V13).
--
-- Same portability contract as V1-V46: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses this file
-- alongside V1-V46 at build time, so the generated NpcTypeData table and record appear with no extra
-- configuration.

CREATE TABLE npc_type_data (
    npc_name    VARCHAR(64) NOT NULL,
    data_key    VARCHAR(32) NOT NULL,
    data_value  VARCHAR(64) NOT NULL,
    CONSTRAINT pk_npc_type_data PRIMARY KEY (npc_name, data_key)
);

CREATE INDEX idx_npc_type_data_npc ON npc_type_data (npc_name);
