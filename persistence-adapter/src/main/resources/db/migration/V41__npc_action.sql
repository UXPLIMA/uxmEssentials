-- Adds the ordered click-action chain to an NPC (V38-V40 are the name row, look toggle, equipment and glow),
-- the common "actions" surface. The existing single click_command column (V38) is kept and still
-- runs first on a click; this is the richer, additive mechanism. A list of typed actions, each with a click
-- trigger (LEFT/RIGHT/ANY) and a type (run-console, run-player, message, action-bar, title, sound, connect).
--
-- The actions live in a SEPARATE child table keyed (npc_name, ordinal), NOT an opaque JSON blob: the
-- architecture persistence invariant is that every queryable fact is a first-class column, so an action is a
-- row that can be selected, ordered and edited in place. ordinal is the 0-based run order; click_trigger and
-- type are the enum names; value is the raw operator payload the runner interprets per type. The
-- foreign-key-like relationship is enforced by the application (it rewrites the whole action set on every
-- save), kept dialect-portable by avoiding an ON DELETE CASCADE clause SQLite gates behind a pragma, the
-- delete removes the actions and the row in one transaction, mirroring hologram_lines (V13).
--
-- The click-trigger column is named click_trigger, not trigger: TRIGGER is a reserved keyword in MySQL/MariaDB
-- (and a non-portable column name there without backticks), so the prefixed name keeps the plain unquoted DDL
-- accepted on every backend, in line with the portability contract below.
--
-- Same portability contract as V1-V40: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses this file
-- alongside V1-V40 at build time, so the generated NpcAction table and record appear with no extra configuration.

CREATE TABLE npc_action (
    npc_name       VARCHAR(64)  NOT NULL,
    ordinal        INT          NOT NULL,
    click_trigger  VARCHAR(16)  NOT NULL,
    type           VARCHAR(32)  NOT NULL,
    value          VARCHAR(512) NOT NULL,
    CONSTRAINT pk_npc_action PRIMARY KEY (npc_name, ordinal)
);

CREATE INDEX idx_npc_action_npc ON npc_action (npc_name);
