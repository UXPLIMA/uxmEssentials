-- Adds the remaining appearance and behaviour fields of an NPC (V38-V47 are the name row, look toggle,
-- equipment/glow, the action chain, the entity type, the widened equipment payload, the pose/scale columns and the
-- per-type metadata child table). An NPC can now carry a shown display name distinct from its id, a slim/classic
-- skin-model variant, a mirror-skin toggle, collision and tab-visibility toggles, per-NPC view/turn distance
-- overrides, the on-fire/invisible/silent state flags, and a per-NPC interaction-cooldown override, the full
-- per-NPC surface.
--
-- display_name is the shown label (a MiniMessage string), nullable so a NULL or empty value hides the name and
-- leaves only the id (the tab/profile name is unchanged). skin_slim is a SMALLINT 0/1 flag for the body model
-- (0 classic/Steve, 1 slim/Alex), NOT NULL DEFAULT 0 so an older row reads back classic. mirror_skin, collidable,
-- show_in_tab, on_fire, invisible and silent are SMALLINT 0/1 toggles, each NOT NULL DEFAULT 0 so an older row reads
-- back with the prior behaviour (no mirror, no collision, hidden from tab, not on fire, visible, audible).
-- view_distance and turn_distance are nullable REAL per-NPC overrides of the module render/look ranges, a NULL
-- means "use the module default". interaction_cooldown_millis is a BIGINT per-NPC override of the global click
-- cooldown, NOT NULL DEFAULT 0 where 0 means "use the module default". All columns are purely additive and leave
-- every stored NPC unchanged.
--
-- Same portability contract as V1-V47: plain ALTER TABLE ADD COLUMN statements with a constant DEFAULT in the
-- subset SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's
-- DDLDatabase parses this file alongside V1-V47 at build time, so the generated Npc record gains the new columns
-- with no extra configuration.

ALTER TABLE npc ADD COLUMN display_name                TEXT;
ALTER TABLE npc ADD COLUMN skin_slim                   SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN mirror_skin                 SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN collidable                  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN show_in_tab                 SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN on_fire                     SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN invisible                   SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN silent                      SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE npc ADD COLUMN view_distance               REAL;
ALTER TABLE npc ADD COLUMN turn_distance               REAL;
ALTER TABLE npc ADD COLUMN interaction_cooldown_millis BIGINT   NOT NULL DEFAULT 0;
