-- Adds the per-NPC look-at-player toggle to the npc table (V38). When set, the fake player rotates to
-- face each nearby viewer individually (the per-viewer look behaviour); when clear it keeps the fixed facing it
-- was placed with. A boolean is stored as a SMALLINT (0/1) so the column shape is identical on SQLite (the
-- default), MySQL/MariaDB and PostgreSQL, none of which agree on a native BOOLEAN literal in the subset we
-- keep portable. Existing rows default to 1 so an NPC created before this migration starts tracking players,
-- matching the default a freshly created NPC carries.
--
-- jOOQ's DDLDatabase parses this ALTER alongside V1-V38 at build time, so the generated Npc record gains the
-- look_at_player column with no extra configuration.

ALTER TABLE npc ADD COLUMN look_at_player SMALLINT NOT NULL DEFAULT 1;
