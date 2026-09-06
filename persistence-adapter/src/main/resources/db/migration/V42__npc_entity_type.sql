-- Adds the entity type an NPC renders as (V38-V41 are the name row, look toggle, equipment/glow and the action
-- chain). An NPC is no longer always a fake player: entity_type names the Bukkit EntityType it spawns as
-- PLAYER (the default, the fake-player path with a tab entry and a skin) or any living mob (VILLAGER, ZOMBIE, …),
-- the "type" surface.
--
-- The column is the uppercase EntityType name, the same string the domain carries, so the render adapter resolves
-- it to a real type and decides PLAYER-vs-mob without the domain ever touching Bukkit. NOT NULL with a PLAYER
-- default means every existing row reads back as a fake player exactly as before. The migration is purely
-- additive and leaves every stored NPC unchanged.
--
-- Same portability contract as V1-V41: a plain ALTER TABLE ADD COLUMN with a constant DEFAULT in the subset
-- SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's
-- DDLDatabase parses this file alongside V1-V41 at build time, so the generated Npc record gains the entity_type
-- column with no extra configuration.

ALTER TABLE npc ADD COLUMN entity_type VARCHAR(64) NOT NULL DEFAULT 'PLAYER';
