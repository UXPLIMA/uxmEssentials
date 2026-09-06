-- Adds equipment and a glowing outline to the npc table (V38, look toggle V39).
-- Equipment is six nullable material-name columns, one per wearable slot, rather than a serialized
-- map: each holds a Bukkit material NAME (e.g. DIAMOND_HELMET), the simplest portable shape and one an
-- operator can inspect or edit directly. A NULL column is an empty slot. The adapter resolves each name to a
-- real item at render time and skips a name the live server does not know, so a name that ages out of the
-- registry degrades to an empty slot rather than a broken NPC.
--
-- glowing is a SMALLINT 0/1 (the same portable boolean shape as look_at_player in V39, SQLite, MySQL/MariaDB
-- and PostgreSQL do not all agree on a native BOOLEAN literal in the subset we keep portable), defaulting to 0
-- so every existing NPC keeps its current un-glowing look. glow_color is the optional outline colour NAME
-- (e.g. RED), nullable for the default white outline.
--
-- Same portability contract as V1-V39: plain ALTER TABLE ... ADD COLUMN statements in the subset all three
-- backends accept. jOOQ's DDLDatabase parses this file alongside V1-V39 at build time, so the generated Npc
-- record gains the new columns with no extra configuration.

ALTER TABLE npc ADD COLUMN equip_mainhand VARCHAR(64);
ALTER TABLE npc ADD COLUMN equip_offhand  VARCHAR(64);
ALTER TABLE npc ADD COLUMN equip_head     VARCHAR(64);
ALTER TABLE npc ADD COLUMN equip_chest    VARCHAR(64);
ALTER TABLE npc ADD COLUMN equip_legs     VARCHAR(64);
ALTER TABLE npc ADD COLUMN equip_feet     VARCHAR(64);
ALTER TABLE npc ADD COLUMN glowing        SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN glow_color     VARCHAR(16);
