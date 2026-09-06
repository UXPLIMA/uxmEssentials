-- Adds the per-player viewer set behind a MANUAL-visibility hologram (V13 is the name row and its lines, V35
-- the appearance, V36 the visibility mode/permission/distance, V43 the rotation). A MANUAL hologram is hidden
-- from everyone until an operator runs /hologram show <name> <player>; the shown players are recorded here so
-- the set survives a restart, and /hologram hide removes a row. ALL and PERMISSION holograms keep no rows here.
--
-- The viewers live in a SEPARATE child table keyed (hologram_name, player_uuid), NOT an opaque JSON blob: the
-- architecture persistence invariant is that every queryable fact is a first-class column, so a shown player is
-- a row that can be selected, added and removed in place. player_uuid is the viewer's uuid as canonical 36-char
-- text, identical on every backend. The foreign-key-like relationship is enforced by the application (it deletes
-- the viewer rows alongside the name row), kept dialect-portable by avoiding an ON DELETE CASCADE clause SQLite
-- gates behind a pragma. The hologram delete removes the viewers and the row in one transaction, mirroring
-- hologram_lines (V13) and npc_action (V41).
--
-- Same portability contract as V1-V43: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses this file
-- alongside V1-V43 at build time, so the generated HologramManualViewer table and record appear with no extra
-- configuration.

CREATE TABLE hologram_manual_viewer (
    hologram_name  VARCHAR(64)  NOT NULL,
    player_uuid    VARCHAR(36)  NOT NULL,
    CONSTRAINT pk_hologram_manual_viewer PRIMARY KEY (hologram_name, player_uuid)
);

CREATE INDEX idx_hologram_manual_viewer_hologram ON hologram_manual_viewer (hologram_name);
