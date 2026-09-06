-- Links a hologram to an NPC so it floats above that NPC and follows it as the NPC moves (the
-- linkWithNpc/unlinkWithNpc feature). When set, the renderer anchors the hologram at the linked NPC's position
-- plus a small vertical offset rather than its own stored coordinates, and re-anchors on every NPC move; an
-- absent (NULL) value is the default: the hologram stays anchored to its own stored location. Unlinking clears
-- the column back to NULL.
--
-- The column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite, MySQL/MariaDB and
-- PostgreSQL (SQLite gates a few DEFAULT forms behind pragmas); an absent value reads back as NULL and the
-- mapper resolves that to "not linked", so every existing row keeps anchoring to its own coordinates with no
-- data migration. The type mirrors the existing string columns (the V36 visibility_permission node): a VARCHAR
-- holding the linked NPC's canonical name, which is at most 64 characters (the NpcName length cap).
--
--   linked_npc_name   the canonical name of the NPC the hologram follows; NULL = not linked

ALTER TABLE holograms ADD COLUMN linked_npc_name VARCHAR(64);
